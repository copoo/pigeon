/**
 * Dianping.com Inc.
 * Copyright (c) 2003-${year} All Rights Reserved.
 */
package com.dianping.pigeon.remoting.netty.invoker.codec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;

import com.dianping.pigeon.remoting.common.codec.SerializerFactory;
import com.dianping.pigeon.remoting.common.domain.InvocationResponse;
import com.dianping.pigeon.remoting.common.domain.InvocationSerializable;
import com.dianping.pigeon.remoting.common.util.Constants;
import com.dianping.pigeon.remoting.common.util.TimelineUtils;
import com.dianping.pigeon.remoting.common.util.TimelineUtils.Phase;
import com.dianping.pigeon.remoting.netty.codec.AbstractEncoder;
import com.dianping.pigeon.remoting.netty.codec.NettyCodecUtils;

public class InvokerEncoder extends AbstractEncoder {

	public InvokerEncoder() {
		super();
	}

	public Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
		NettyCodecUtils.setAttachment(ctx, Constants.ATTACHMENT_RETRY, msg);
		Object[] message = (Object[]) msg;
		Object encoded = super.encode(ctx, channel, message[0]);
		// TIMELINE_client_encoded
		TimelineUtils.time((InvocationSerializable) message[0], TimelineUtils.getLocalIp(), Phase.ClientEncoded);
		return encoded;
	}

	@Override
	public void doFailResponse(Channel channel, InvocationResponse response) {
		List<InvocationResponse> respList = new ArrayList<InvocationResponse>();
		respList.add(response);
		Channels.fireMessageReceived(channel, respList);
	}

	@Override
	public void serialize(byte serializerType, ChannelBufferOutputStream os, Object obj, Channel channel)
			throws IOException {
		SerializerFactory.getSerializer(serializerType).serializeRequest(os, obj);
	}

}
