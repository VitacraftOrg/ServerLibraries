package net.vitacraft.serverlibraries.api.event;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.util.ActionResult;
import net.vitacraft.serverlibraries.api.event.events.commands.CommandRegistrationCallbackEvent;
import net.vitacraft.serverlibraries.api.event.events.entities.*;
import net.vitacraft.serverlibraries.api.event.events.lifecycle.*;
import net.vitacraft.serverlibraries.api.event.events.networking.*;
import net.vitacraft.serverlibraries.api.event.events.players.PlayerChangeWorldEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class EventsRegistry {
    private static final List<Listener> listeners = new ArrayList<>();
    private static boolean initialized = false;

    public static void initializeGlobalListener() {
        // Ensure that the client listener is only initialized once to prevent duplicate event registration
        if (initialized) return;
        initializeEntities();
        initializeLifecycle();
        initializeCommands();
        initializeNetworking();
        initialized = true;
    }

    public static void registerListener(Listener listener){
        listeners.add(listener);
    }

    public static boolean dispatchEvent(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        List<Handler> handlers = new ArrayList<>();
        for (Listener listener : listeners) {
            handlers.addAll(getHandlers(listener, event));
        }

        // Sort handlers by priority
        handlers.sort(Comparator.comparing(handler -> handler.priority));

        // Dispatch events in sorted order
        for (Handler handler : handlers) {
            if (!event.isCancelled() || handler.priority == EventPriority.MONITOR) {
                handler.invoke(event);
            }
            if (event.isCancelled() && handler.priority != EventPriority.MONITOR) {
                return false;
            }
        }
        return true;
    }

    private static List<Handler> getHandlers(Listener listener, Event event) {
        List<Handler> handlers = new ArrayList<>();
        Class<?> listenerClass = listener.getClass();
        Method[] methods = listenerClass.getMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(EventHandler.class)) {
                EventHandler eventHandler = method.getAnnotation(EventHandler.class);
                EventPriority priority = eventHandler.priority();
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 1 && parameterTypes[0].isAssignableFrom(event.getClass())) {
                    handlers.add(new Handler(listener, method, priority));
                }
            }
        }
        return handlers;
    }

    private static class Handler {
        private final Object listener;
        private final Method method;
        private final EventPriority priority;

        public Handler(Object listener, Method method, EventPriority priority) {
            this.listener = listener;
            this.method = method;
            this.priority = priority;
        }

        public void invoke(Event event) {
            try {
                method.invoke(listener, event);
            } catch (Exception e) {
                System.err.println("Error dispatching event: " + event.getClass().getSimpleName() + " to listener: " + listener.getClass().getSimpleName() + " with method: " + method.getName() + " - " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void initializeCommands(){
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, dedicated) -> {
            CommandRegistrationCallbackEvent event = new CommandRegistrationCallbackEvent(dispatcher, registryAccess, dedicated);
            dispatchEvent(event);
        });
    }

    private static void initializeEntities(){
        EntityElytraEvents.ALLOW.register(entity -> {
            ElytraAllowEvent event = new ElytraAllowEvent(entity);
            return dispatchEvent(event);
        });
        EntityElytraEvents.CUSTOM.register((entity, tickElytra) -> {
            ElytraCustomEvent event = new ElytraCustomEvent(entity, tickElytra);
            return dispatchEvent(event);
        });
        EntitySleepEvents.ALLOW_BED.register((entity, sleepingPos, state, vanillaResult) -> {
            SleepAllowBedEvent event = new SleepAllowBedEvent(entity, sleepingPos, state, vanillaResult);
            return getActionResultFromEventDispatch(dispatchEvent(event));
        });
        EntitySleepEvents.ALLOW_NEARBY_MONSTERS.register((entity, sleepingPos, vanillaResult) -> {
            AllowNearbyMonstersSleepEvent event = new AllowNearbyMonstersSleepEvent(entity, sleepingPos, vanillaResult);
            return getActionResultFromEventDispatch(dispatchEvent(event));
        });
        EntitySleepEvents.ALLOW_RESETTING_TIME.register(entity -> {
            SleepAllowResettingTimeEvent event = new SleepAllowResettingTimeEvent(entity);
            return dispatchEvent(event);
        });
        EntitySleepEvents.ALLOW_SETTING_SPAWN.register((entity, sleepingPos) -> {
            SleepAllowSettingSpawnEvent event = new SleepAllowSettingSpawnEvent(entity, sleepingPos);
            return dispatchEvent(event);
        });
        EntitySleepEvents.ALLOW_SLEEP_TIME.register((entity, sleepingPos, vanillaResult) -> {
            SleepAllowTimeEvent event = new SleepAllowTimeEvent(entity, sleepingPos, vanillaResult);
            return getActionResultFromEventDispatch(dispatchEvent(event));
        });
        EntitySleepEvents.START_SLEEPING.register((entity, sleepingPos) -> {
            SleepStartEvent event = new SleepStartEvent(entity, sleepingPos);
            dispatchEvent(event);
        });
        EntitySleepEvents.STOP_SLEEPING.register((entity, sleepingPos) -> {
            SleepStopEvent event = new SleepStopEvent(entity, sleepingPos);
            dispatchEvent(event);
        });
        EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) -> {
            PlayerStartTrackingEvent event = new PlayerStartTrackingEvent(trackedEntity, player);
            dispatchEvent(event);
        });
        EntityTrackingEvents.STOP_TRACKING.register((trackedEntity, player) -> {
            PlayerStopTrackingEvent event = new PlayerStopTrackingEvent(trackedEntity, player);
            dispatchEvent(event);
        });
        ServerEntityWorldChangeEvents.AFTER_ENTITY_CHANGE_WORLD.register((originalEntity, newEntity, origin, destination) -> {
            EntityChangeWorldEvent event = new EntityChangeWorldEvent(originalEntity, newEntity, origin, destination);
            dispatchEvent(event);
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((originalPlayerEntity, origin, destination) -> {
            PlayerChangeWorldEvent event = new PlayerChangeWorldEvent(originalPlayerEntity, origin, destination);
            dispatchEvent(event);
        });
    }

    private static void initializeLifecycle(){
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            TagsLoadedEvent event = new TagsLoadedEvent(registries, client);
            dispatchEvent(event);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerStartedEvent event = new ServerStartedEvent(server);
            dispatchEvent(event);
        });
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ServerStartingEvent event = new ServerStartingEvent(server);
            dispatchEvent(event);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ServerStoppedEvent event = new ServerStoppedEvent(server);
            dispatchEvent(event);
        });
        /*ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ServerStoppingEvent event = new ServerStoppingEvent(server);
            dispatchEvent(event);
        });*/
        ServerLifecycleEvents.START_DATA_PACK_RELOAD.register((server, serverResourceManager) -> {
            DataPackReloadStartEvent event = new DataPackReloadStartEvent(server, serverResourceManager);
            dispatchEvent(event);
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, serverResourceManager, successful) -> {
            DataPackReloadEndEvent event = new DataPackReloadEndEvent(server, serverResourceManager, successful);
            dispatchEvent(event);
        });
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> {
            DataPackSyncEvent event = new DataPackSyncEvent(player, joined);
            dispatchEvent(event);
        });
    }

    private static void initializeNetworking(){
        ServerLoginConnectionEvents.INIT.register((handler, server) -> {
            LoginConnectionInitEvent event = new LoginConnectionInitEvent(handler, server);
            dispatchEvent(event);
        });
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            LoginQueryStartEvent event = new LoginQueryStartEvent(handler, server, sender, synchronizer);
            dispatchEvent(event);
        });
        ServerLoginConnectionEvents.DISCONNECT.register((handler, server) -> {
            LoginDisconnectEvent event = new LoginDisconnectEvent(handler, server);
            dispatchEvent(event);
        });
        ServerPlayConnectionEvents.INIT.register((handler, server) -> {
            PlayerConnectionEvent event = new PlayerConnectionEvent(handler, server);
            dispatchEvent(event);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PlayerJoinEvent event = new PlayerJoinEvent(handler, sender, server);
            dispatchEvent(event);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PlayerDisconnectEvent event = new PlayerDisconnectEvent(handler, server);
            dispatchEvent(event);
        });
    }

    private static ActionResult getActionResultFromEventDispatch(boolean dispatchEvent) {
        if (dispatchEvent) {
            return ActionResult.SUCCESS;
        } else {
            return ActionResult.PASS;
        }
    }
}
