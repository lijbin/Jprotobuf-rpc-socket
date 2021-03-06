/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.baidu.jprotobuf.pbrpc.transport.handler;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

import com.baidu.jprotobuf.pbrpc.data.RpcDataPackage;
import com.baidu.jprotobuf.pbrpc.data.RpcResponseMeta;
import com.baidu.jprotobuf.pbrpc.transport.RpcClient;
import com.baidu.jprotobuf.pbrpc.transport.RpcClientCallState;

/**
 * RPC client service handler upon receive response data from server.
 * 
 * @author xiemalin
 * @since 1.0
 */
public class RpcClientServiceHandler extends SimpleChannelUpstreamHandler {

    /**
     * log this class
     */
    private static final Logger LOG = Logger.getLogger(RpcClientServiceHandler.class.getName());
    
    /**
     * RPC client
     */
    private RpcClient rpcClient;

    /**
     * @param rpcClient
     */
    public RpcClientServiceHandler(RpcClient rpcClient) {
        super();
        this.rpcClient = rpcClient;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.jboss.netty.channel.SimpleChannelUpstreamHandler#messageReceived(
     * org.jboss.netty.channel.ChannelHandlerContext,
     * org.jboss.netty.channel.MessageEvent)
     */
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (!(e.getMessage() instanceof RpcDataPackage)) {
            return;
        }

        RpcDataPackage dataPackage = (RpcDataPackage) e.getMessage();
        Long correlationId = dataPackage.getRpcMeta().getCorrelationId();
        RpcClientCallState state = rpcClient.removePendingRequest(correlationId);
        
        Integer errorCode = ErrorCodes.ST_SUCCESS;
        RpcResponseMeta response = dataPackage.getRpcMeta().getResponse();
        if (response != null) {
            errorCode = response.getErrorCode();
        }
        
        if (! ErrorCodes.isSuccess(errorCode)) {
            if (state != null) {
                state.handleFailure(errorCode, response.getErrorText());
            } else {
                ctx.sendUpstream(e);
                throw new Exception(response.getErrorText());
            }
        } else {
            if (state != null) {
                state.setDataPackage(dataPackage);
                state.handleResponse(state.getDataPackage());
            } 
        }

        ctx.sendUpstream(e);
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see
     * org.jboss.netty.channel.SimpleChannelHandler#exceptionCaught(org.jboss
     * .netty.channel.ChannelHandlerContext,
     * org.jboss.netty.channel.ExceptionEvent)
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        LOG.log(Level.SEVERE, e.getCause().getMessage(), e.getCause());
        
        if (e.getChannel().isOpen()) {
            e.getChannel().close();
        }
    }

}
