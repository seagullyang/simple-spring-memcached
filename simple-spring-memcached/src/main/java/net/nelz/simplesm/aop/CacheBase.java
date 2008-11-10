package net.nelz.simplesm.aop;

import net.nelz.simplesm.exceptions.*;
import net.nelz.simplesm.annotations.*;
import net.spy.memcached.*;
import org.aspectj.lang.*;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.*;

import java.lang.reflect.*;
import java.security.*;

/**
Copyright (c) 2008  Nelson Carpentier

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 */
public class CacheBase {

	static final String SEPARATOR = ":";

	protected MemcachedClientIF cache;
	CacheKeyMethodStore methodStore;

	public void setCache(MemcachedClientIF cache) {
		this.cache = cache;
	}

	public void setMethodStore(CacheKeyMethodStore methodStore) {
		this.methodStore = methodStore;
	}

	protected Method getMethodToCache(final JoinPoint jp) throws NoSuchMethodException {
		final Signature sig = jp.getSignature();
		if (!(sig instanceof MethodSignature)) {
			throw new InvalidAnnotationException("This annotation is only valid on a method.");
		}
		final MethodSignature msig = (MethodSignature) sig;
		final Object target = jp.getTarget();
		return target.getClass().getMethod(msig.getName(), msig.getParameterTypes());
	}

	protected String generateObjectId(final Method keyMethod, final Object keyObject) throws Exception {
		final String objectId = (String) keyMethod.invoke(keyObject, null);
		if (objectId == null || objectId.length() < 1) {
			throw new RuntimeException("Got an empty key value from " + keyMethod.getName());
		}
		return objectId;
	}

	protected String buildCacheKey(final String objectId, final String namespace) {
		if (objectId == null || objectId.length() < 1) {
			throw new InvalidParameterException("Ids for objects in the cache must be at least 1 character long.");
		}
		if (namespace == null || namespace.length() < 1) {
			throw new InvalidParameterException("Namespace values must be at least 1 character long.");
		}
		return namespace + SEPARATOR + objectId;
	}

	protected Object getKeyObject(final int keyIndex,
	                             final JoinPoint jp,
	                             final Method methodToCache) throws Exception {
		final Object[] args = jp.getArgs();
		if (args.length <= keyIndex) {
			throw new InvalidParameterException(String.format(
					"A key index of %s is too big for the number of arguments in [%s]",
					keyIndex,
					methodToCache.toString()));
		}
		final Object keyObject = args[keyIndex];
		if (keyObject == null) {
			throw new InvalidParameterException(String.format(
					"The argument passed into [%s] at index %s is null.",
					methodToCache.toString(),
					keyIndex));
		}
		return keyObject;
	}

	protected Method getKeyMethod(final Object keyObject) throws NoSuchMethodException {
		final Method storedMethod = methodStore.find(keyObject.getClass());
		if (storedMethod != null) { return storedMethod; }
		final Method[] methods = keyObject.getClass().getDeclaredMethods();
		Method targetMethod = null;
		for (final Method method : methods) {
			if (method != null && method.getAnnotation(CacheKeyMethod.class) != null) {
				if (method.getParameterTypes().length > 0) {
					throw new InvalidAnnotationException(String.format(
							"Method [%s] must have 0 arguments to be annotated with [%s]",
							method.toString(),
							CacheKeyMethod.class.getName()));
				}
				if (!String.class.equals(method.getReturnType())) {
					throw new InvalidAnnotationException(String.format(
							"Method [%s] must return a String to be annotated with [%s]",
							method.toString(),
							CacheKeyMethod.class.getName()));
				}
				if (targetMethod != null) {
					throw new InvalidAnnotationException(String.format(
							"Class [%s] should have only one method annotated with [%s]. See [%s] and [%s]",
							keyObject.getClass().getName(),
							CacheKeyMethod.class.getName(),
							targetMethod.getName(),
							method.getName()));
				}
				targetMethod = method;
			}
		}

		if (targetMethod == null) {
			targetMethod = keyObject.getClass().getMethod("toString", null);
		}

		methodStore.add(keyObject.getClass(), targetMethod);

		return targetMethod;
	}


}
