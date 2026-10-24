package net.staro.nomnom.api.event.bus;

import net.staro.nomnom.api.event.listener.EventListener;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.api.event.listener.SafeEventListener;
import net.staro.nomnom.api.event.listener.SafeListener;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

/**
 * The event bus for registering and distributing events.
 *
 * <p>Events may be posted from any thread (packet events come from the network thread) while
 * modules subscribe and unsubscribe on the main thread. Listener lists are copy-on-write, so
 * a post always iterates a stable snapshot: it can't throw a ConcurrentModificationException,
 * and a listener may safely unsubscribe itself (or its module) while handling an event.
 *
 * @see EventListener
 */
public class EventBus {
    /**
     * Listeners are put into a list based on their event class, sorted by priority.
     */
    private final Map<Class<?>, CopyOnWriteArrayList<EventListener>> listeners = new ConcurrentHashMap<>();
    /**
     * Subscribed objects (compared by identity) mapped to the event types they listen to.
     */
    private final Map<Object, List<Class<?>>> subscriptions = Collections.synchronizedMap(new IdentityHashMap<>());
    /**
     * Stores factory functions for creating listeners based on their annotations.
     */
    private final Map<Class<? extends Annotation>, BiFunction<Object, Method, EventListener>> listenerFactories = new HashMap<>();

    public EventBus() {
        // registering a regular listener
        registerListenerFactory(Listener.class, (instance, method) ->
                new EventListener(instance, method, method.getAnnotation(Listener.class).priority().getVal())
        );
        // registering a listener that checks mc.player, mc.world and mc.interactionManager for being not null
        registerListenerFactory(SafeListener.class, (instance, method) ->
                new SafeEventListener(instance, method, method.getAnnotation(SafeListener.class).priority().getVal())
        );
    }

    /**
     * Registers a factory function for creating a specific type of listener based on its annotation.
     * The current Library provides {@link EventListener}.
     * For creating more listeners either implement {@link EventListener} or extend {@link EventListener} and register them in the constructor with this method.
     *
     * @param annotationType The annotation class associated with the listener type.
     * @param factory The function that creates the listener instance given the object and method.
     */
    public void registerListenerFactory(Class<? extends Annotation> annotationType, BiFunction<Object, Method, EventListener> factory) {
        listenerFactories.put(annotationType, factory);
    }

    /**
     * Posts an event to all registered listeners. Listeners are keyed by the exact event class.
     * @param event The event object.
     */
    public void post(Object event) {
        List<EventListener> list = listeners.get(event.getClass());
        if (list == null) {
            return;
        }

        // The copy-on-write list gives this loop a snapshot, so listeners may (un)subscribe.
        for (EventListener l : list) {
            l.invoke(event);
        }
    }

    /**
     * Subscribes listeners.
     * @param instance is a listener.
     */
    public synchronized void subscribe(Object instance) {
        addListeners(getListeningMethods(instance.getClass()), instance);
    }

    /**
     * Unsubscribes listeners.
     * @param instance is a listener
     */
    public synchronized void unsubscribe(Object instance) {
        removeListeners(getListeningMethods(instance.getClass()), instance);
        subscriptions.remove(instance);
    }

    public boolean isSubscribed(Object object) {
        return subscriptions.containsKey(object);
    }

    /**
     * Turns methods we know are listeners into listener objects.
     *
     * @param methods  the methods we want to turn into listeners
     * @param instance the method's class' instance (null if methods are static)
     */
    private void addListeners(List<Method> methods, Object instance) {
        List<Class<?>> subscribedEvents = subscriptions.computeIfAbsent(instance, k -> new ArrayList<>());
        for (var method : methods) {
            Class<?> eventType = getEventParameterType(method);
            CopyOnWriteArrayList<EventListener> list =
                    listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
            for (var annotation : method.getAnnotations()) {
                if (listenerFactories.containsKey(annotation.annotationType())) {
                    BiFunction<Object, Method, EventListener> factory = listenerFactories.get(annotation.annotationType());
                    EventListener listener = factory.apply(instance, method);
                    int index = getPriorityIndex(list, listener.getPriority());
                    list.add(index, listener);
                    subscribedEvents.add(eventType);
                    break;
                }
            }
        }
    }

    /**
     * Finds where a listener with the given priority goes: after every listener with a higher
     * priority and before the ones with the same or a lower priority.
     */
    private int getPriorityIndex(List<EventListener> list, int priority) {
        int index = list.size();
        while (index > 0 && list.get(index - 1).getPriority() <= priority) {
            index--;
        }
        return index;
    }

    /**
     * Removes Listeners by looping over their respective lists
     *
     * @param methods  the methods we want to remove
     * @param instance method's class' instance (null if methods are static)
     */
    private void removeListeners(List<Method> methods, Object instance) {
        for (var method : methods) {
            Class<?> eventType = getEventParameterType(method);
            CopyOnWriteArrayList<EventListener> list = listeners.get(eventType);
            if (list == null) {
                continue;
            }

            list.removeIf(l -> l.getMethod().equals(method) && l.getInstance() == instance);
        }
    }

    private List<Method> getListeningMethods(Class<?> clazz) {
        ArrayList<Method> listening = new ArrayList<>();
        for (var method : clazz.getDeclaredMethods()) {
            for (var annotation : method.getDeclaredAnnotations()) {
                if (listenerFactories.containsKey(annotation.annotationType()) && method.getParameterCount() == 1) {
                    listening.add(method);
                }
            }
        }
        return listening;
    }

    private static Class<?> getEventParameterType(Method method) {
        if (method.getParameterCount() != 1) {
            return null;
        }

        return method.getParameters()[0].getType();
    }

}