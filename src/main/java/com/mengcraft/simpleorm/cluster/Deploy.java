package com.mengcraft.simpleorm.cluster;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.SneakyThrows;
import redis.clients.jedis.params.SetParams;

import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    private final Map<Long, Long> aliveMap = Maps.newHashMap();
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
            aliveMap.values().removeIf(last -> (t - last) > ttl);
        }
        int deployNum = options.getDeployPerNode() - deployMap.size();
        if (deployNum > 0) {
            int maxDeployNum = options.getDeploy() - deployMap.size() - aliveMap.size();
            if (maxDeployNum > 0) {
                deploy(Math.min(deployNum, maxDeployNum));
            }
        }
    }

    private void doMsgSync() {
        ByteBuf data = Unpooled.buffer();
        data.writeByte(PROTO_SYNC);
        data.writeShort(deployMap.size());
        deployMap.keySet().forEach(data::writeLong);
        publish(msgChannel, data);
    }

    @SneakyThrows
    private void deploy(int deployNum) {
        jedis(jedis -> {
            String key = String.format("cluster:%s:deploy:%s:deploying", system().getName(), options.getName());
            String locked = jedis.set(key,
                    String.valueOf(getId()),
                    SetParams.setParams().nx().ex(options.getTtl() / 2));// lock half keepAlive time
            if ("OK".equals(locked)) {
                int tSize = deployMap.size() + deployNum;
                for (int i = 0; i < deployNum; i++) {
                    system().deploy(newHandler()).thenAcceptAsync(instance -> deploy0(instance, tSize), executor);
                }
            }
            return locked;
        });
    }

    @SneakyThrows
    private Handler newHandler() {
        return cls.newInstance();
    }

    private void deploy0(Handler instance, int limitSize) {
        deployMap.put(instance.getId(), instance);
        if (deployMap.size() >= limitSize) {
            doMsgSync();
        }
    }

    @Override
    protected void onMessage(String subject, HandlerId sender, ByteBuf msg) {
        if (sender.getId() != getId()) {// no reply self message
            byte id = msg.readByte();
            if (id == PROTO_INIT) {// sync
                onMsgInit(sender);
            } else if (id == PROTO_INIT_REPLY) {// sync reply
                onMsgInitReply(msg);
            } else if (id == PROTO_SYNC) {
                onMsgSync(msg);
            }
        }
    }

    private void onMsgSync(ByteBuf msg) {
        int len = msg.readShort();
        for (int i = 0; i < len; i++) {
            aliveMap.put(msg.readLong(), System.currentTimeMillis());
        }
    }

    private void onMsgInitReply(ByteBuf msg) {
        if (state == STATE_INIT) {
            if (msg.readByte() == 1) {
                int len = msg.readShort();
                for (int i = 0; i < len; i++) {
                    aliveMap.put(msg.readLong(), System.currentTimeMillis());
                }
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
            deployMap.keySet().forEach(data::writeLong);
        }
        sendMessage(sender, data);
    }

    static class Consensus {

        private int total;
        private int reply;
    }
}
