/*
 * Copyright 2002-2006 the original author or authors.
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
package org.springframework.osgi.service.support;

import org.springframework.util.Assert;

/**
 * Simple retry template.
 * 
 * @author Costin Leau
 * 
 */
public class RetryTemplate {

	public static final long DEFAULT_WAIT_TIME = 1000;

	public static final int DEFAULT_RETRY_NUMBER = 3;

	private long waitTime = DEFAULT_WAIT_TIME;

	private int retryNumbers = DEFAULT_RETRY_NUMBER;

	public Object execute(RetryCallback callback) {
		Assert.notNull(callback, "callback is required");

		int count = 0;
		do {
			Object result = callback.doWithRetry();
			if (callback.isComplete(result))
				return result;

			// task is not complete - retry
			count++;
			try {
				Thread.sleep(waitTime);
			}
			catch (InterruptedException ex) {
				throw new RuntimeException("retry failed; interrupted while sleeping", ex);
			}
		} while (count < retryNumbers);

		return null;
	}

	public int getRetryNumbers() {
		return retryNumbers;
	}

	public void setRetryNumbers(int retryNumbers) {
		this.retryNumbers = retryNumbers;
	}

	public long getWaitTime() {
		return waitTime;
	}

	public void setWaitTime(long waitTime) {
		this.waitTime = waitTime;
	}

}
