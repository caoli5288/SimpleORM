package com.mengcraft.simpleorm.lib;

import com.google.common.base.Preconditions;
import lombok.SneakyThrows;
import sun.misc.Unsafe;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

public class Types {

    private static final MethodHandles.Lookup LOOKUP = lookup();

    public static MethodHandles.Lookup lookupPrivileged(Class<?> cls) {
        return LOOKUP.in(cls);
    }

    @SneakyThrows
    static MethodHandles.Lookup lookup() {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe) field.get(Unsafe.class);
        field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
        long offset = unsafe.staticFieldOffset(field);
        return (MethodHandles.Lookup) unsafe.getObject(MethodHandles.Lookup.class, offset);
    }

    Types() {
    }

    @SneakyThrows
    public static <T> T lambdaPrivileged(Method method, Class<T> cls) {
        Preconditions.checkState(cls.isInterface());
        Method sam = sam(cls);
        Objects.requireNonNull(sam, "Class is not SAM class. " + cls);
        // Workaround for private accessor
        MethodHandles.Lookup lookup = lookupPrivileged(method.getDeclaringClass());
        MethodHandle mh = lookup.unreflect(method);
        CallSite ct = LambdaMetafactory.metafactory(lookup,
                sam.getName(),
                MethodType.methodType(cls),
                MethodType.methodType(sam.getReturnType(), sam.getParameterTypes()),
                mh,
                MethodType.methodType(method.getReturnType(), mh.type().parameterArray()));
        return (T) ct.getTarget().invoke();
    }

    static Method sam(Class<?> cls) {
        Method sam = null;
        for (Method method : cls.getMethods()) {
            if (Modifier.isAbstract(method.getModifiers())) {
                if (sam != null) {
                    return null;
                }
                sam = method;
            }
        }
        return sam;
    }
}
