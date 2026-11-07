package io.github.sekerator.kingdomworld.compat.xaero;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Checks and links against classes of Xaero's maps, which offer no official API for other mods. Before a highlighter
 * is handed over, {@link #unimplemented(Class)} proves that it implements every abstract method its Xaero base class
 * has now (a newer version may have added one, which would otherwise fail inside Xaero's rendering). Highlighters
 * are added to Xaero's registry by replacing its private list, the only way in after Xaero closed the registry.
 */
final class XaeroLinks {
    private XaeroLinks() {
    }

    /**
     * Abstract methods of the base classes that {@code type} does not implement, as readable names; empty when the
     * class fits the Xaero version installed.
     */
    static List<String> unimplemented(Class<?> type) {
        List<String> missing = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (Modifier.isAbstract(method.getModifiers()) && !implemented(type, method)) {
                    missing.add(c.getSimpleName() + "." + method.getName() + Arrays.toString(method.getParameterTypes()));
                }
            }
        }
        return missing;
    }

    private static boolean implemented(Class<?> type, Method abstractMethod) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Method found = c.getDeclaredMethod(abstractMethod.getName(), abstractMethod.getParameterTypes());
                if (!Modifier.isAbstract(found.getModifiers())) {
                    return true;
                }
            } catch (NoSuchMethodException e) {
                // Look further up.
            }
        }
        return false;
    }

    /**
     * Adds a highlighter to a registry of Xaero's maps unless one of its kind is there already.
     *
     * @return whether it was added
     */
    static boolean addToRegistry(Object registry, Object highlighter) throws ReflectiveOperationException {
        Field field = listField(registry.getClass());
        field.setAccessible(true);
        List<?> current = (List<?>) field.get(registry);
        for (Object present : current) {
            if (present.getClass() == highlighter.getClass()) {
                return false;
            }
        }
        List<Object> next = new ArrayList<>(current);
        next.add(highlighter);
        field.set(registry, Collections.unmodifiableList(next));
        return true;
    }

    /**
     * Reads a private field of a given type, used for the registry of Xaero's Minimap.
     */
    static Object fieldOfType(Object owner, Class<?> type) throws ReflectiveOperationException {
        for (Class<?> c = owner.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && type.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field.get(owner);
                }
            }
        }
        throw new NoSuchFieldException("no field of type " + type.getName() + " in " + owner.getClass().getName());
    }

    /**
     * Whether a registry of the Minimap already shows our World Map highlighter through the Minimap's wrapper of World
     * Map highlighters, so that land is not drawn twice.
     */
    static boolean wrapsOurs(Object registry) throws ReflectiveOperationException {
        Field field = listField(registry.getClass());
        field.setAccessible(true);
        for (Object present : (List<?>) field.get(registry)) {
            for (Field inner : present.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(inner.getModifiers()) || inner.getType().isPrimitive()) {
                    continue;
                }
                inner.setAccessible(true);
                Object value = inner.get(present);
                if (value != null && value.getClass().getName().equals(XaeroLinks.class.getPackageName() + ".WorldMapKingdomHighlighter")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Field listField(Class<?> type) throws NoSuchFieldException {
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && List.class.isAssignableFrom(field.getType())) {
                return field;
            }
        }
        throw new NoSuchFieldException("no list of highlighters in " + type.getName());
    }
}
