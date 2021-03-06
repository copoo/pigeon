/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.remoting.common.domain;

public interface InvocationContext {

	InvocationRequest getRequest();

	void setRequest(InvocationRequest request);

	InvocationResponse getResponse();

}
