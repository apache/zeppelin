/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.resource;

import com.google.gson.Gson;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import org.apache.zeppelin.common.JsonSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * Information and reference to the resource
 */
public class Resource implements JsonSerializable, Serializable {
  private static final Gson gson = new Gson();

  private final transient Object r;
  private final transient LocalResourcePool pool;
  private final boolean serializable;
  private final ResourceId resourceId;
  private final String className;


  /**
   * Create local resource
   *
   * @param resourceId
   * @param r          must not be null
   */
  Resource(LocalResourcePool pool, ResourceId resourceId, Object r) {
    this.r = r;
    this.pool = pool;
    this.resourceId = resourceId;
    this.serializable = r instanceof Serializable;
    this.className = r.getClass().getName();
  }

  /**
   * Create remote object
   *
   * @param resourceId
   */
  Resource(LocalResourcePool pool, ResourceId resourceId, boolean serializable, String className) {
    this.r = null;
    this.pool = pool;
    this.resourceId = resourceId;
    this.serializable = serializable;
    this.className = className;
  }

  public ResourceId getResourceId() {
    return resourceId;
  }

  public String getClassName() {
    return className;
  }

  /**
   * @return null when this is remote resource and not serializable.
   */
  public Object get() {
    if (isLocal() || isSerializable()) {
      return r;
    } else {
      return null;
    }
  }

  public boolean isSerializable() {
    return serializable;
  }

  /**
   * if it is remote object
   *
   * @return
   */
  public boolean isRemote() {
    return !isLocal();
  }

  /**
   * Whether it is locally accessible or not
   *
   * @return
   */
  public boolean isLocal() {
    return true;
  }

  /**
   * Invoke a method without param
   * @param methodName
   * @return
   */
  public Object invokeMethod(String methodName) {
    return invokeMethod(methodName, (Class []) null, (Object []) null);
  }

  /**
   * Invoke a method and store result in ResourcePool
   * @param methodName
   * @param returnResourceName
   * @return
   */
  public Resource invokeMethod(String methodName, String returnResourceName) {
    return invokeMethod(methodName, (Class []) null, (Object []) null, returnResourceName);
  }

  /**
   * Invoke a method with automatic parameter type inference
   * @param methodName
   * @param params
   * @return
   * @throws ClassNotFoundException
   */
  public Object invokeMethod(String methodName, Object [] params)
          throws ClassNotFoundException {
    return invokeMethod(methodName, (Type[]) null, params);
  }

  /**
   * Invoke a method with automatic parameter type inference
   * @param methodName
   * @param params python interpreter convert python array '[]' to ArrayList through py4j
   * @return
   * @throws ClassNotFoundException
   */
  public Object invokeMethod(
          String methodName, ArrayList params)
          throws ClassNotFoundException {
    Object[] paramsArray = params.toArray(new Object[]{});
    return invokeMethod(methodName, paramsArray);
  }

  /**
   * Invoke a method with automatic parameter type inference and store result in ResourcePool
   * @param methodName
   * @param params
   * @param returnResourceName
   * @return
   * @throws ClassNotFoundException
   */
  public Resource invokeMethod(String methodName, Object [] params, String returnResourceName)
          throws ClassNotFoundException {
    return (Resource) invokeMethod(methodName, (Type[]) null, params, returnResourceName);
  }

  /**
   * Invoke a method with automatic parameter type inference and store result in ResourcePool
   * @param methodName
   * @param params python interpreter convert python array '[]' to ArrayList through py4j
   * @param returnResourceName
   * @return
   * @throws ClassNotFoundException
   */
  public Resource invokeMethod(
          String methodName, ArrayList params, String returnResourceName)
          throws ClassNotFoundException {
    Object[] paramsArray = params.toArray(new Object[]{});
    return invokeMethod(methodName, paramsArray, returnResourceName);
  }

  /**
   * Invoke a method with given parameter class names
   * @param methodName
   * @param paramTypes list of fully qualified class name
   * @param params
   * @return
   * @throws ClassNotFoundException
   */
  public Object invokeMethod(
          String methodName, String[] paramTypes, Object[] params)
          throws ClassNotFoundException {
    Type [] types = typeFromName(paramTypes);
    return invokeMethod(methodName, types, params);
  }

  /**
   * Invoke a method with given parameter class names
   * @param methodName
   * @param paramTypes list of fully qualified class name. python interpreter convert python array '[]' to ArrayList through py4j
   * @param params python interpreter convert python array '[]' to ArrayList through py4j
   * @return
   * @throws ClassNotFoundException
   */
  public Object invokeMethod(
          String methodName, ArrayList<String> paramTypes, ArrayList params)
          throws ClassNotFoundException {
    String[] paramTypesArray = paramTypes.toArray(new String[]{});
    Object[] paramsArray = params.toArray(new Object[]{});
    return invokeMethod(methodName, paramTypesArray, paramsArray);
  }

  /**
   * Invoke a method with given parameter class names and store result in ResourcePool
   * @param methodName
   * @param paramTypes
   * @param params
   * @param returnResourceName
   * @return
   * @throws ClassNotFoundException
   */
  public Resource invokeMethod(
          String methodName, String[] paramTypes, Object[] params, String returnResourceName)
          throws ClassNotFoundException {
    Type [] types = typeFromName(paramTypes);
    return (Resource) invokeMethod(methodName, types, params, returnResourceName);
  }


  public Resource invokeMethod(
          String methodName, ArrayList<String> paramTypes, ArrayList params, String returnResourceName)
          throws ClassNotFoundException {
    String[] paramTypesArray = paramTypes.toArray(new String[]{});
    Object[] paramsArray = params.toArray(new Object[]{});
    return invokeMethod(methodName, paramTypesArray, paramsArray, returnResourceName);
  }

  /**
   * Invoke a method with give parameter types
   * @param methodName
   * @param types
   * @param params
   * @return
   * @throws ClassNotFoundException
   */
  public Object invokeMethod(
          String methodName, Type[] types, Object[] params)
          throws ClassNotFoundException {
    return invokeMethod(methodName, types, params, null);
  }

  /**
   * Invoke a method with given parameter type and store result in ResourcePool
   * @param methodName
   * @param types
   * @param params
   * @param returnResourceName
   * @return
   * @throws ClassNotFoundException
   */
  public Object invokeMethod(
          String methodName, Type[] types, Object[] params, String returnResourceName) throws ClassNotFoundException {
    Type[] methodTypes = null;
    Object [] methodParams = null;
    if (types != null) {
      methodTypes = types;
      methodParams = params;
    } else {
      // inference method param types
      boolean found = false;
      Method[] methods = r.getClass().getDeclaredMethods();
      for (Method m : methods) {
        if (!m.getName().equals(methodName)) {
          continue;
        }
        Type[] paramTypes = m.getGenericParameterTypes();
        Object[] paramValues = new Object[paramTypes.length];

        int pidx = 0;
        for (int i = 0; i < paramTypes.length; i++) {
          if (pidx == params.length) {  // not enough param for this method signature
            continue;
          } else {
            paramValues[i] = params[pidx++];
          }
        }

        if (pidx == params.length) {  // param number does not match
          found = true;
          methodParams = paramValues;
          methodTypes = paramTypes;
          break;
        }
      }

      if (!found) {
        throw new ClassNotFoundException("No method found for given parameters");
      }
    }

    Class[] classes = classFromType(methodTypes);

    if (returnResourceName == null) {
      return invokeMethod(methodName, classes, convertParams(methodTypes, methodParams));
    } else {
      return invokeMethod(methodName, classes, convertParams(methodTypes, methodParams), returnResourceName);
    }
  }

  /**
   * Call a method of the object that this resource holds
   *
   * @param methodName name of method to call
   * @param paramTypes method parameter types
   * @param params     method parameter values
   * @return return value of the method
   */
  public Object invokeMethod(
      String methodName, Class[] paramTypes, Object[] params) {
    if (r != null) {
      try {
        Method method = r.getClass().getMethod(
            methodName,
            paramTypes);
        method.setAccessible(true);
        Object ret = method.invoke(r, params);
        return ret;
      } catch (Exception e) {
        logException(e);
        return null;
      }
    } else {
      return null;
    }
  }

  /**
   * Call a method of the object that this resource holds and save return value as a resource
   *
   * @param methodName         name of method to call
   * @param paramTypes         method parameter types
   * @param params             method parameter values
   * @param returnResourceName name of resource that return value will be saved
   * @return Resource that holds return value
   */
  public Resource invokeMethod(
      String methodName, Class[] paramTypes, Object[] params, String returnResourceName) {
    if (r != null) {
      try {
        Method method = r.getClass().getMethod(
            methodName,
            paramTypes);
        Object ret = method.invoke(r, params);
        pool.put(
            resourceId.getNoteId(),
            resourceId.getParagraphId(),
            returnResourceName,
            ret
        );
        return pool.get(
            resourceId.getNoteId(),
            resourceId.getParagraphId(),
            returnResourceName);
      } catch (Exception e) {
        logException(e);
        return null;
      }
    } else {
      return null;
    }
  }

  public static ByteBuffer serializeObject(Object o) throws IOException {
    if (o == null || !(o instanceof Serializable)) {
      return null;
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      ObjectOutputStream oos;
      oos = new ObjectOutputStream(out);
      oos.writeObject(o);
      oos.close();
      out.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return ByteBuffer.wrap(out.toByteArray());
  }

  public static Object deserializeObject(ByteBuffer buf)
      throws IOException, ClassNotFoundException {
    if (buf == null) {
      return null;
    }
    InputStream ins = ByteBufferInputStream.get(buf);
    ObjectInputStream oin;
    Object object = null;

    oin = new ObjectInputStream(ins);
    object = oin.readObject();
    oin.close();
    ins.close();

    return object;
  }

  private void logException(Exception e) {
    Logger logger = LoggerFactory.getLogger(Resource.class);
    logger.error(e.getMessage(), e);
  }

  public String toJson() {
    return gson.toJson(this);
  }

  public static Resource fromJson(String json) {
    return gson.fromJson(json, Resource.class);
  }

  private ParameterizedType [] typeFromName(String [] classNames) throws ClassNotFoundException {
    if (classNames == null) {
      return null;
    }
    ParameterizedType[] types = new ParameterizedType[classNames.length];
    for (int i = 0; i < classNames.length; i++) {
      types[i] = typeFromName(classNames[i]);
    }
    return types;
  }

  private ParameterizedType typeFromName(String commaSeparatedClasses) throws ClassNotFoundException {
    String[] classNames = commaSeparatedClasses.split(",");
    Class [] arguments;

    if (classNames.length > 1) {
      arguments = new Class[classNames.length - 1];
      for (int i = 1; i < classNames.length; i++) {
        arguments[i - 1] = loadClass(classNames[i]);
      }
    } else {
      arguments = new Class[0];
    }

    Class rawType = loadClass(classNames[0]);

    return new ParameterizedType() {
      @Override
      public Type[] getActualTypeArguments() {
        return arguments;
      }

      @Override
      public Type getRawType() {
        return rawType;
      }

      @Override
      public Type getOwnerType() {
        return null;
      }
    };
  }

  private Class [] classFromType(Type[] types) throws ClassNotFoundException {
    Class[] cls = new Class[types.length];
    for (int i = 0; i < types.length; i++) {
      if (types[i] instanceof ParameterizedType) {
        String typeName = ((ParameterizedType) types[i]).getRawType().getTypeName();
        cls[i] = loadClass(typeName);
      } else {
        cls[i] = loadClass(types[i].getTypeName());
      }
    }
    return cls;
  }


  private Object [] convertParams(Type[] types, Object [] params) {
    Object [] converted = new Object[types.length];

    for (int i = 0; i < types.length; i++) {
      Type type = types[i];
      String typeName;
      if (type instanceof ParameterizedType) {
        typeName = ((ParameterizedType) type).getRawType().getTypeName();
      } else {
        typeName = type.getTypeName();
      }

      Object param = params[i];
      if (param == null) {
        converted[i] = null;
      } else if (param.getClass().getName().equals(typeName)) {
        converted[i] = param;
      } else {
        // try to convert param
        converted[i] = gson.fromJson(gson.toJson(param), type);
      }
    }

    return converted;
  }

  private Class loadClass(String className) throws ClassNotFoundException {
    switch(className) {
      case "byte":
        return byte.class;
      case "short":
        return short.class;
      case "int":
        return int.class;
      case "long":
        return long.class;
      case "float":
        return float.class;
      case "double":
        return double.class;
      case "boolean":
        return boolean.class;
      case "char":
        return char.class;
      default:
        return getClass().getClassLoader().loadClass(className);
    }
  }
}
