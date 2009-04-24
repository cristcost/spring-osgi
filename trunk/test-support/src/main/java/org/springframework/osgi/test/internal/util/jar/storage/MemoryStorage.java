/*
 * Copyright 2006-2009 the original author or authors.
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
package org.springframework.osgi.test.internal.util.jar.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

/**
 * Memory based storage. This class writes the information to a byte array.
 * 
 * @author Costin Leau
 * 
 */
public class MemoryStorage implements Storage {

	private ByteArrayOutputStream buffer = new ByteArrayOutputStream();

	public void dispose() {
		buffer = new ByteArrayOutputStream(0);
	}

	public InputStream getInputStream() {
		return new ByteArrayInputStream(buffer.toByteArray());
	}

	public OutputStream getOutputStream() {
		return buffer;
	}

	public Resource getResource() {
		return new ByteArrayResource(buffer.toByteArray());
	}
}
