package com.mengcraft.simpleorm.cluster;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.SneakyThrows;
import redis.clients.jedis.params.SetParams;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class Deploy extends Handler {
    // Constants
    // States
    public static final int STATE_INIT = 0;
    public static final int STATE_RUNNING = 1;
    public static final int STATE_CLOSED = 2;
    // Protocols
    // Init: <byte: header>
    public static final int PROTO_INIT = 0;
    public static final int PROTO_INIT_REPLY = 1;
    public static final int PROTO_SYNC = 2;
    // Fields
    private final DeployOptions options;
    private final Map<Long, Info> aliveMap = Maps.newHashMap();// <DeployId, Num>
    private final Map<Long, Handler> deployMap = Maps.newHashMap();
    private final Class<? extends Handler> cls;
    private Consensus consensus;
    private String msgChannel;
    private int state = STATE_INIT;


    Deploy(Class<? extends Handler> cls, DeployOptions options) {
        Preconditions.checkArgument(options.valid());
        this.cls = cls;
        this.options = options;
    }

    @Override
    protected void onInit() {
        msgChannel = "deploy:" + options.getName();
        listen(msgChannel).thenRun(() -> {
            init0();
            long ttl = options.getTtl();
            schedule(this::onRun, TimeUnit.SECONDS, ttl, ttl);
        });
    }

    @Override
    protected void onClose() {
        state = STATE_CLOSED;
        deployMap.values().forEach(Handler::close);
    }

    private void init0() {
        ByteBuf data = Unpooled.buffer();
        data.writeByte(PROTO_INIT);
        publish(msgChannel, data).thenAccept(num -> {
            if (num <= 1) {// At least 1 because includes self
                state = STATE_RUNNING;
                onRun();
            } else {
                consensus = new Consensus();
                consensus.total = num.intValue() - 1;
            }
        });
    }

    private void onRun() {
        // re-init
        if (state == 0) {
            init0();
        } else if (state == 1) {
            onRunning();
        }
    }

    private void onRunning() {
        if (!deployMap.isEmpty()) {
            deployMap.values().removeIf(obj -> obj.status() == STATE_CLOSED);
            if (!deployMap.isEmpty()) {
                doMsgSync();
            }
        }
        // clean timeout
        if (!aliveMap.isEmpty()) {
            long t = System.currentTimeMillis();
            long ttl = options.getTtl() * 2500;
            aliveMap.values().removeIf(info -> (t - info.t) > ttl);
        }
        int deployNum = options.getDeployPerNode() - deployMap.size();
        if (deployNum > 0) {
            int maxDeployNum = options.getDeploy() - allDeployments();
            if (maxDeployNum > 0) {
                deploy();
            }
        }
    }

    public int allDeployments() {
        int val = deployMap.size();
        // remotes
        for (Info info : aliveMap.values()) {
            val += info.deployments;
        }
        return val;
    }

    private void doMsgSync() {
        ByteBuf data = Unpooled.buffer();
        data.writeByte(PROTO_SYNC);
        data.writeShort(deployMap.size());
        publish(msgChannel, data);
    }

    @SneakyThrows
    private void deploy() {
        jedis(jedis -> {
            String key = String.format("cluster:%s:deploy:%s:deploying", system().getName(), options.getName());
            String locked = jedis.set(key,
                    String.valueOf(getId()),
                    SetParams.setParams().nx().ex(options.getTtl()));// lock half keepAlive time
            if ("OK".equals(locked)) {
                // Delayed to avoid race condition
                executor.schedule(() -> deploy(key), 0, TimeUnit.MILLISECONDS);
            }
            return locked;
        });
    }

    private int deploy(String key) {
        int deployNum = options.getDeployPerNode() - deployMap.size();
        if (deployNum > 0) {
            int maxDeployNum = options.getDeploy() - allDeployments();
            if (maxDeployNum > 0) {
                return onDeploy(key, Math.min(deployNum, maxDeployNum));
            }
        }
        // No deployments, release deploy key
        jedis(jedis -> jedis.del(key));
        return 0;
    }

    private int onDeploy(String key, int deployNum) {
        return jedis(jedis -> {
            CompletableFuture<?>[] list = IntStream.range(0, deployNum)
                    .mapToObj(l -> system().deploy(newHandler())
                            .thenAcceptAsync(deployed -> deployMap.put(deployed.getId(), deployed), executor))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(list)
                    .thenRunAsync(this::doMsgSync, executor)
                    .whenCompleteAsync((__, t) -> jedis.del(key));
            return deployNum;
        });
    }

    @SneakyThrows
    private Handler newHandler() {
        return cls.newInstance();
    }

    @Override
    protected void onMessage(String subject, HandlerId sender, ByteBuf msg) {
        if (sender.getId() != getId()) {// no reply self message
            byte id = msg.readByte();
            if (id == PROTO_INIT) {// sync
                onMsgInit(sender);
            } else if (id == PROTO_INIT_REPLY) {// sync reply
                onMsgInitReply(sender, msg);
            } else if (id == PROTO_SYNC) {
                onMsgSync(sender, msg);
            }
        }
    }

    private void onMsgSync(HandlerId handlerId, ByteBuf msg) {
        // short: deploy num
        long deployId = handlerId.getId();
        Info info = new Info();
        info.deployments = msg.readUnsignedShort();
        aliveMap.put(deployId, info);
    }

    private void onMsgInitReply(HandlerId handlerId, ByteBuf msg) {
        if (state == STATE_INIT) {
            if (msg.readByte() == STATE_RUNNING) {
                long deployId = handlerId.getId();
                Info info = new Info();
                info.deployments = msg.readUnsignedShort();
                aliveMap.put(deployId, info);
            }
            consensus.reply++;
            // check fully done
            if (consensus.reply >= consensus.total) {
                consensus = null;
                state = STATE_RUNNING;
                onRun();
            }
        }
    }

    private void onMsgInit(HandlerId sender) {
        ByteBuf data = Unpooled.buffer();
        data.writeByte(PROTO_INIT_REPLY);
        data.writeByte(state);
        if (state == STATE_RUNNING) {
            data.writeShort(deployMap.size());
        }
        sendMessage(sender, data);
    }

    static class Consensus {

        private int total;
        private int reply;
    }

    static class Info {
        private final long t = System.currentTimeMillis();
        private int deployments;
    }
}
