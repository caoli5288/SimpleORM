package com.mengcraft.simpleorm.lib;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;

public class URLClassLoaderAccessor {

    private static final IAccessor ACCESSOR = Impl.HANDLE == null ?
            new Impl2() :
            new Impl();

    public static void addUrl(URLClassLoader cl, URL url) {
        ACCESSOR.addUrl(cl, url);
    }

    public interface IAccessor {

        void addUrl(URLClassLoader cl, URL url);
    }

    private static class Impl implements IAccessor {

        private static final Method HANDLE = setup();

        private static Method setup() {
            try {
                Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                addURL.setAccessible(true);
                return addURL;
            } catch (Exception e) {
                // noop
            }
            return null;
        }

        @Override
        public void addUrl(URLClassLoader cl, URL url) {
            try {
                HANDLE.invoke(cl, url);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class Impl2 implements IAccessor {

        private static final sun.misc.Unsafe HANDLE = setup();

        private static Unsafe setup() {
            try {
                Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                return (sun.misc.Unsafe) f.get(null);
            } catch (Exception e) {
                // noop
            }
            return null;
        }

        @Override
        public void addUrl(URLClassLoader cl, URL url) {
            try {
                Object ucp = lookup(URLClassLoader.class, cl, "ucp");
                Collection<URL> unopenedUrls = (Collection<URL>) lookup(ucp.getClass(), ucp, "unopenedUrls");
                Collection<URL> path = (Collection<URL>) lookup(ucp.getClass(), ucp, "path");
                unopenedUrls.add(url);
                path.add(url);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private static Object lookup(Class<?> cls, Object obj, String fieldName) throws NoSuchFieldException {
            Field field = cls.getDeclaredField(fieldName);
            long offset = HANDLE.objectFieldOffset(field);
            return HANDLE.getObject(obj, offset);
        }
    }
}
