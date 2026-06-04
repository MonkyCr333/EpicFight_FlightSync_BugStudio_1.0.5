package bugstudio.efsync.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

public final class Reflect {
    private Reflect() {}


    public static boolean registerForgeEventHandler(Object listener) {
        if (listener == null) return false;
        try {
            Class<?> forgeClass = Class.forName("net.minecraftforge.common.MinecraftForge");
            Field eventBusField = forgeClass.getField("EVENT_BUS");
            Object eventBus = eventBusField.get(null);
            if (eventBus == null) return false;

            Method register = findCompatibleMethod(eventBus.getClass(), "register", Object.class);
            if (register == null) {
                register = findOneArgMethod(eventBus.getClass(), "register");
            }
            if (register == null) return false;

            register.setAccessible(true);
            register.invoke(eventBus, listener);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Object invokeAny(Object target, String... methodNames) {
        if (target == null) return null;
        Class<?> c = target.getClass();
        for (String name : methodNames) {
            try {
                Method m = findMethod(c, name);
                if (m != null) {
                    m.setAccessible(true);
                    return m.invoke(target);
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }


    public static Object invokeStaticAny(Class<?> c, String... methodNames) {
        if (c == null) return null;
        for (String name : methodNames) {
            try {
                Method m = findMethod(c, name);
                if (m != null) {
                    m.setAccessible(true);
                    return m.invoke(null);
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static Object invokeAnyInt(Object target, int value, String... methodNames) {
        if (target == null) return null;
        Class<?> c = target.getClass();
        for (String name : methodNames) {
            try {
                Method m = findMethod(c, name, int.class);
                if (m == null) m = findMethod(c, name, Integer.TYPE);
                if (m != null) {
                    m.setAccessible(true);
                    return m.invoke(target, value);
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static Object fieldAny(Object target, String... fieldNames) {
        if (target == null) return null;
        Class<?> c = target.getClass();
        for (String name : fieldNames) {
            try {
                Field f = findField(c, name);
                if (f != null) {
                    f.setAccessible(true);
                    return f.get(target);
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static boolean booleanFieldAny(Object target, boolean def, String... fieldNames) {
        Object v = fieldAny(target, fieldNames);
        return v instanceof Boolean b ? b.booleanValue() : def;
    }

    public static int intFromMethod(Object target, int def, String... methodNames) {
        Object v = invokeAny(target, methodNames);
        return v instanceof Number n ? n.intValue() : def;
    }

    public static String stringFromMethod(Object target, String... methodNames) {
        Object v = invokeAny(target, methodNames);
        return v == null ? null : String.valueOf(v);
    }

    public static UUID uuidFromMethod(Object target, String... methodNames) {
        Object v = invokeAny(target, methodNames);
        return v instanceof UUID u ? u : null;
    }

    @SuppressWarnings("unchecked")
    public static Set<String> stringSetFromMethod(Object target, String... methodNames) {
        Object v = invokeAny(target, methodNames);
        if (v instanceof Set<?>) {
            try {
                return (Set<String>) v;
            } catch (ClassCastException ignored) {
            }
        }
        return Set.of();
    }

    public static double doubleFieldAny(Object target, double def, String... fieldNames) {
        Object v = fieldAny(target, fieldNames);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    public static float floatFromMethod(Object target, float def, String... methodNames) {
        Object v = invokeAny(target, methodNames);
        return v instanceof Number n ? n.floatValue() : def;
    }

    public static boolean invokeVoidFloatAny(Object target, float value, String... methodNames) {
        if (target == null) return false;
        Class<?> c = target.getClass();
        for (String name : methodNames) {
            try {
                Method m = findMethod(c, name, float.class);
                if (m == null) m = findMethod(c, name, Float.TYPE);
                if (m != null) {
                    m.setAccessible(true);
                    m.invoke(target, value);
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static boolean invokeVoidFloatBooleanAny(Object target, float value, boolean flag, String... methodNames) {
        if (target == null) return false;
        Class<?> c = target.getClass();
        for (String name : methodNames) {
            try {
                Method m = findMethod(c, name, float.class, boolean.class);
                if (m != null) {
                    m.setAccessible(true);
                    m.invoke(target, value, flag);
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static boolean setFloatFieldAny(Object target, float value, String... fieldNames) {
        if (target == null) return false;
        Class<?> c = target.getClass();
        for (String name : fieldNames) {
            try {
                Field f = findField(c, name);
                if (f != null) {
                    f.setAccessible(true);
                    f.setFloat(target, value);
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }


    public static boolean setBooleanFieldAny(Object target, boolean value, String... fieldNames) {
        if (target == null) return false;
        Class<?> c = target.getClass();
        for (String name : fieldNames) {
            try {
                Field f = findField(c, name);
                if (f != null) {
                    f.setAccessible(true);
                    f.setBoolean(target, value);
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }


    private static Method findCompatibleMethod(Class<?> c, String name, Class<?>... parameterTypes) {
        Class<?> cur = c;
        while (cur != null) {
            for (Method m : cur.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != parameterTypes.length) {
                    continue;
                }
                Class<?>[] actual = m.getParameterTypes();
                boolean ok = true;
                for (int i = 0; i < actual.length; i++) {
                    if (!actual[i].isAssignableFrom(parameterTypes[i])) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    return m;
                }
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static Method findOneArgMethod(Class<?> c, String name) {
        Class<?> cur = c;
        while (cur != null) {
            for (Method m : cur.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 1) {
                    return m;
                }
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static Method findMethod(Class<?> c, String name) {
        Class<?> cur = c;
        while (cur != null) {
            for (Method m : cur.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    return m;
                }
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static Method findMethod(Class<?> c, String name, Class<?>... parameterTypes) {
        Class<?> cur = c;
        while (cur != null) {
            try {
                return cur.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }

    private static Field findField(Class<?> c, String name) {
        Class<?> cur = c;
        while (cur != null) {
            try {
                return cur.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }
}
