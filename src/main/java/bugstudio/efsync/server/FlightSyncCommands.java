package bugstudio.efsync.server;

import bugstudio.efsync.Log;
import bugstudio.efsync.config.FlightSyncConfig;
import bugstudio.efsync.network.FlightSyncNetwork;
import bugstudio.efsync.util.Reflect;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.function.Supplier;

public final class FlightSyncCommands {
    @SubscribeEvent
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void onRegisterCommands(RegisterCommandsEvent event) {
        // Mohist puede exponer Brigadier con firmas/covariant returns distintas a las usadas al compilar.
        // Por eso NO usamos llamadas directas tipo builder.requires(...), builder.then(...),
        // builder.executes(...) ni dispatcher.register(...). Todo lo sensible va por reflexión.
        Object rootBuilder = LiteralArgumentBuilder.literal("efsync");
        Object reloadBuilder = LiteralArgumentBuilder.literal("reload");

        Command command = ctx -> {
            Object source = Reflect.invokeAny(ctx, "getSource");
            return reloadIfAllowed(source);
        };

        boolean commandAttached = invokeOneArgByName(reloadBuilder, "executes", command);
        boolean childAttached = invokeOneArgByName(rootBuilder, "then", reloadBuilder);

        if (!commandAttached || !childAttached) {
            Log.warn("No se pudo construir /efsync reload via Brigadier reflejado. commandAttached="
                    + commandAttached + ", childAttached=" + childAttached);
            return;
        }

        Object dispatcher = event.getDispatcher();
        Object root = invokeNoArgByName(dispatcher, "getRoot");
        Object node = invokeNoArgByName(rootBuilder, "build");

        if (root != null && node != null && invokeOneArgByName(root, "addChild", node)) {
            Log.info("Comando /efsync registrado via Brigadier root.addChild.");
            return;
        }

        // Fallback: intentar register por reflexión si el runtime sí lo expone con una clase compatible.
        if (invokeOneArgByName(dispatcher, "register", rootBuilder)) {
            Log.info("Comando /efsync registrado via dispatcher.register reflejado.");
            return;
        }

        Log.warn("No se pudo registrar /efsync reload. El mod seguirá cargando sin comando de recarga.");
    }

    private static boolean hasPermission(Object source, int level) {
        Object v = Reflect.invokeAnyInt(source, level, "hasPermission", "m_6761_");
        return v instanceof Boolean b ? b : true;
    }

    private static int reloadIfAllowed(Object source) {
        if (!hasPermission(source, 2)) {
            sendFailure(source, "[EpicFightFlightSync] No tienes permiso para usar /efsync reload.");
            return 0;
        }
        return reload(source);
    }

    private static int reload(Object source) {
        List<String> warnings = FlightSyncConfig.load();
        int synced = syncConfigToOnlinePlayers(source);

        String msg = "[EpicFightFlightSync] Config recargada. Clientes sincronizados realmente: " + synced
                + ". remoteFlightMode=" + FlightSyncConfig.remoteFlightMode
                + ", forceRemoteFlightMotion=" + FlightSyncConfig.forceRemoteFlightMotion
                + ", debug=" + FlightSyncConfig.debug;
        sendSuccess(source, msg);
        Log.info(msg);

        for (String warning : warnings) {
            String w = "[EpicFightFlightSync] Config warning: " + warning;
            sendFailure(source, w);
            Log.warn(warning);
        }
        return 1;
    }

    private static int syncConfigToOnlinePlayers(Object source) {
        Object server = Reflect.invokeAny(source, "getServer", "m_81377_");
        Object playerList = Reflect.invokeAny(server, "getPlayerList", "m_6846_");
        Object players = Reflect.invokeAny(playerList, "getPlayers", "m_11314_");
        if (!(players instanceof List<?> list)) {
            return 0;
        }

        int sent = 0;
        for (Object o : list) {
            if (o instanceof ServerPlayer player) {
                if (FlightSyncNetwork.syncConfigToPlayer(player)) {
                    sent++;
                }
            }
        }
        return sent;
    }

    private static void sendSuccess(Object source, String text) {
        Object component = literalComponent(text);
        if (component == null || source == null) return;

        // 1.20.1 Mojmap: sendSuccess(Supplier<Component>, boolean)
        // Runtime SRG: usually m_288197_(Supplier<Component>, boolean)
        Supplier<Object> supplier = () -> component;
        if (invokeNamed(source, "sendSuccess", new Class<?>[]{Supplier.class, boolean.class}, supplier, Boolean.TRUE)) return;
        if (invokeNamed(source, "m_288197_", new Class<?>[]{Supplier.class, boolean.class}, supplier, Boolean.TRUE)) return;

        // Fallback: sendSystemMessage(Component)
        if (invokeOneArgByName(source, "sendSystemMessage", component)) return;
        invokeOneArgByName(source, "m_213846_", component);
    }

    private static void sendFailure(Object source, String text) {
        Object component = literalComponent(text);
        if (component == null || source == null) return;
        if (invokeOneArgByName(source, "sendFailure", component)) return;
        if (invokeOneArgByName(source, "m_81352_", component)) return;
        if (invokeOneArgByName(source, "sendSystemMessage", component)) return;
        invokeOneArgByName(source, "m_213846_", component);
    }

    private static Object literalComponent(String text) {
        try {
            Class<?> c = Class.forName("net.minecraft.network.chat.Component");
            for (String name : new String[]{"literal", "m_237113_"}) {
                for (Method m : c.getMethods()) {
                    if (Modifier.isStatic(m.getModifiers()) && m.getName().equals(name)
                            && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == String.class) {
                        m.setAccessible(true);
                        return m.invoke(null, text);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean invokeNamed(Object target, String name, Class<?>[] paramTypes, Object... args) {
        try {
            Method m = target.getClass().getMethod(name, paramTypes);
            m.setAccessible(true);
            m.invoke(target, args);
            return true;
        } catch (Throwable ignored) {
            try {
                Method m = target.getClass().getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                m.invoke(target, args);
                return true;
            } catch (Throwable ignored2) {
                return false;
            }
        }
    }

    private static Object invokeNoArgByName(Object target, String name) {
        if (target == null) return null;
        Class<?> c = target.getClass();
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                        return m.invoke(target);
                    } catch (Throwable ignored) {
                    }
                }
            }
            c = c.getSuperclass();
        }
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 0) {
                try {
                    m.setAccessible(true);
                    return m.invoke(target);
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static boolean invokeOneArgByName(Object target, String name, Object arg) {
        if (target == null || arg == null) return false;
        Class<?> c = target.getClass();
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isAssignableFrom(arg.getClass())) {
                    try {
                        m.setAccessible(true);
                        m.invoke(target, arg);
                        return true;
                    } catch (Throwable ignored) {
                    }
                }
            }
            c = c.getSuperclass();
        }
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isAssignableFrom(arg.getClass())) {
                try {
                    m.setAccessible(true);
                    m.invoke(target, arg);
                    return true;
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }
}
