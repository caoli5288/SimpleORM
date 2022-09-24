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

    private final DeployOptions options;
    private final Map<Long, Long> aliveMap = Maps.newHashMap();
    private final Map<Long, Handler> deployMap = Maps.newHashMap();
    private final Class<? extends Handler> deployCls;
    private Consensus consensus;
    private String msgChannel;
    // 0: init
    // 1: running
    // 2: closed
    private int state;

    Deploy(Class<? extends Handler> deployCls, DeployOptions options) {
        Preconditions.checkArgument(options.valid());
        this.deployCls = deployCls;
        this.options = options;
    }

    @Override
    protected void onInit() {
        msgChannel = "deploy:" + options.getName();
        listen(msgChannel).thenRun(() -> {
            init0();
            int keepAlive = options.getKeepAlive();
            schedule(this::onRun, TimeUnit.SECONDS, keepAlive, keepAlive);
        });
    }

    @Override
    protected void onClose() {
        state = 2;
        deployMap.values().forEach(Handler::close);
    }

    private void init0() {
        ByteBuf data = Unpooled.buffer();
        data.writeByte(0);
        publish(msgChannel, data).thenAccept(num -> {
            if (num <= 1) {
                state = 1;
                onRun();
            } else {
                consensus = new Consensus();
                consensus.total = num.intValue();
                consensus.reply = 1;
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
        // publish heartbeat
        if (!deployMap.isEmpty()) {
            deployMap.values().removeIf(obj -> obj.status() == 2);
            if (!deployMap.isEmpty()) {
                ByteBuf data = Unpooled.buffer();
                data.writeByte(2);
                data.writeShort(deployMap.size());
                deployMap.keySet().forEach(data::writeLong);
                publish(msgChannel, data);
            }
        }
        // clean timeout
        if (!aliveMap.isEmpty()) {
            long t = System.currentTimeMillis();
            aliveMap.values().removeIf(last -> (t - last) / 1000 > options.getDeadline());
        }
        int upSize = options.getCount() - aliveMap.size() - deployMap.size();
        if (upSize > 0) {
            // Simply use redis lock instead of Rift
            jedis(jedis -> {
                String deploy = String.format("cluster:%s:deploy:%s:deploying", system().getName(), options.getName());
                String set = jedis.set(deploy,
                        String.valueOf(getId()),
                        SetParams.setParams().nx().ex(options.getKeepAlive() / 2));// lock half keepAlive time
                if ("OK".equals(set)) {
                    deploy(upSize);
                }
                return set;
            });
        }
    }

    @SneakyThrows
    private void deploy(int depSize) {
        int limitSize = deployMap.size() + depSize;
        for (int i = 0; i < depSize; i++) {
            system().deploy(deployCls.newInstance()).thenAcceptAsync(instance -> deploy0(instance, limitSize), executor);
        }
    }

    private void deploy0(Handler instance, int limitSize) {
        deployMap.put(instance.getId(), instance);
        if (deployMap.size() >= limitSize) {
            onRunning();
        }
    }

    @Override
    protected void onMessage(String subject, HandlerId sender, ByteBuf msg) {
        if (sender.getId() == getId()) {// no reply self message
            return;
        }
        byte act = msg.readByte();
        if (act == 0) {// sync
            onSync(sender);
        } else if (act == 1) {// sync reply
            onReply(msg);
        } else if (act == 2) {// heartbeat
            onHeartbeat(msg);
        }
    }

    private void onHeartbeat(ByteBuf msg) {
        int len = msg.readShort();
        for (int i = 0; i < len; i++) {
            aliveMap.put(msg.readLong(), System.currentTimeMillis());
        }
    }

    private void onReply(ByteBuf msg) {
        byte mState = msg.readByte();
        if (mState == 1) {
            int len = msg.readShort();
            for (int i = 0; i < len; i++) {
                aliveMap.put(msg.readLong(), System.currentTimeMillis());
            }
        }
        consensus.reply++;
        // check fully done
        if (consensus.reply >= consensus.total) {
            state = 1;
            onRun();
        }
    }

    private void onSync(HandlerId sender) {
        ByteBuf data = Unpooled.buffer();
        data.writeByte(1);
        data.writeByte(state);
        if (state == 1) {
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
