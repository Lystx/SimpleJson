package eu.simplejson.serializer.builder;

import eu.simplejson.JsonHelper;
import eu.simplejson.api.SimpleProvider;
import eu.simplejson.api.modules.ParserModule;
import eu.simplejson.config.JsonConfig;
import eu.simplejson.config.impl.SimpleJsonConfig;
import eu.simplejson.elements.*;
import eu.simplejson.elements.object.JsonObject;
import eu.simplejson.enums.JsonFormat;
import eu.simplejson.serializer.Json;
import eu.simplejson.serializer.adapter.JsonSerializer;
import eu.simplejson.serializer.adapter.provided.base.BooleanSerializer;
import eu.simplejson.serializer.adapter.provided.base.ClassSerializer;
import eu.simplejson.serializer.adapter.provided.base.EnumSerializer;
import eu.simplejson.serializer.adapter.provided.base.StringSerializer;
import eu.simplejson.serializer.adapter.provided.extra.ListSerializer;
import eu.simplejson.serializer.adapter.provided.extra.LiteralSerializer;
import eu.simplejson.serializer.adapter.provided.extra.MapSerializer;
import eu.simplejson.serializer.adapter.provided.extra.UUIDSerializer;
import eu.simplejson.serializer.adapter.provided.number.*;
import eu.simplejson.serializer.modifier.annotation.SerializedField;
import eu.simplejson.serializer.modifier.annotation.SerializedObject;
import eu.simplejson.serializer.modifier.annotation.WrapperClass;
import eu.simplejson.serializer.modifier.strategy.ExcludeStrategy;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import java.io.File;
import java.io.Reader;
import java.lang.reflect.Field;
import java.util.*;

import static eu.simplejson.enums.JsonFormat.UNKNOWN;

@Getter
public class SimpleJson implements Json {

    /**
     * All registered serializers
     */
    private final Map<Class<?>, JsonSerializer<?>> registeredSerializers;

    /**
     * All registered strategies
     */
    private final List<ExcludeStrategy> excludeStrategies;

    /**
     * The format for printing
     */
    private JsonFormat format;

    /**
     * If nulls should be serialized
     */
    private final boolean serializeNulls;

    /**
     * If null should be declared as null or json literal
     */
    private final boolean provideNulledObjectsAsRealNull;

    /**
     * The amount of times a field of an object
     * will be serialized if its the same type as the class
     * (to prevent StackOverFlow)
     */
    private final int serializeSameFieldInstance;

    /**
     * If an object that no serializer was found for
     * should check for all subclasses if a serializer exists
     */
    private final boolean checkSerializersForSubClasses;

    /**
     * If primitive arrays should be written like this : [1, 2, 3, 4, 5, 6]
     */
    @Setter
    private boolean writeArraysSingleLined;

    SimpleJson(JsonFormat format, boolean serializeNulls, int serializeSameFieldInstance, boolean checkSerializersForSubClasses, boolean writeArraysSingleLined, Map<Class<?>, JsonSerializer<?>> registeredSerializers, boolean provideNulledObjectsAsRealNull) {
        this.format = format;
        this.serializeNulls = serializeNulls;
        this.serializeSameFieldInstance = serializeSameFieldInstance;
        this.checkSerializersForSubClasses = checkSerializersForSubClasses;
        this.writeArraysSingleLined = writeArraysSingleLined;

        this.registeredSerializers = registeredSerializers;
        this.provideNulledObjectsAsRealNull = provideNulledObjectsAsRealNull;
        this.excludeStrategies = new ArrayList<>();


        //Registering default serializers
        this.registerSerializer(String.class, new StringSerializer());
        this.registerSerializer(Boolean.class, new BooleanSerializer());
        this.registerSerializer(Class.class, new ClassSerializer());

        //Numbers
        this.registerSerializer(Number.class, new NumberSerializer());
        this.registerSerializer(Integer.class, new IntegerSerializer());
        this.registerSerializer(Double.class, new DoubleSerializer());
        this.registerSerializer(Short.class, new ShortSerializer());
        this.registerSerializer(Float.class, new FloatSerializer());
        this.registerSerializer(Byte.class, new ByteSerializer());
        this.registerSerializer(Long.class, new LongSerializer());

        //Extra values
        this.registerSerializer(UUID.class, new UUIDSerializer());
        this.registerSerializer(Enum.class, new EnumSerializer());
        this.registerSerializer(JsonLiteral.class, new LiteralSerializer());

        //Storage types
        //this.registerSerializer(Iterable.class, new IterableSerializer());
        this.registerSerializer(List.class, new ListSerializer());
        this.registerSerializer(Map.class, new MapSerializer());

    }

    @Override
    public <T> void registerSerializer(Class<T> typeClass, JsonSerializer<T> serializer) {
        this.registeredSerializers.put(typeClass, serializer);
    }

    @Override
    public void registerStrategy(ExcludeStrategy strategy) {
        this.excludeStrategies.add(strategy);
    }


    public static boolean CHANGED_WRITE_SINGLE_LINE = false;
    public static boolean OLD_SINGLE_LINE_VALUE = false;
    public static boolean CHANGED_JSON_FORMAT = false;
    public static JsonFormat OLD_JSON_FORMAT = UNKNOWN;

    private <T> JsonEntity toJson(T base, int currentTry, int maxTry) {
        if (JsonEntity.valueOf(base) != null) {
            return JsonEntity.valueOf(base);
        }

        OLD_SINGLE_LINE_VALUE = this.writeArraysSingleLined;
        CHANGED_WRITE_SINGLE_LINE = false;

        if (base != null && base.getClass().getAnnotation(SerializedObject.class) != null && base.getClass().getAnnotation(SerializedObject.class).customFormat() != UNKNOWN) {
            OLD_JSON_FORMAT = format;
            CHANGED_JSON_FORMAT = true;
            this.format = base.getClass().getAnnotation(SerializedObject.class).customFormat();
        }

        Object obj = base;
        try {
            if (obj == null) {
                return JsonLiteral.NULL;
            }
            if (obj.getClass().isArray()) {
                T[] arrayObj = (T[])obj;
                obj = Arrays.asList(arrayObj);
            }
            JsonEntity jsonEntity;
            boolean contains = this.registeredSerializers.containsKey(obj.getClass());
            Class<?> typeClass = obj.getClass();
            if (checkSerializersForSubClasses) {
                for (Class<?> aClass : JsonHelper.loadAllSubClasses(obj.getClass())) {
                    if (this.registeredSerializers.containsKey(aClass)) {
                        contains = true;
                        typeClass = aClass;
                        break;
                    }
                }
            }
            if (contains) {
                JsonSerializer<T> jsonSerializer = (JsonSerializer<T>) this.registeredSerializers.get(typeClass);
                jsonEntity = jsonSerializer.serialize((T) obj, this, null);
            } else {
                if (obj instanceof Iterable<?> ) {
                    JsonArray jsonArray = new JsonArray();
                    Iterable<?> iterable = (Iterable<?>) obj;

                    for (Object o : iterable) {
                        JsonEntity entity = JsonEntity.valueOf(o);
                        if (entity == null) {
                            jsonArray.add(toJson(o));
                        } else {
                            jsonArray.add(entity);
                        }
                    }

                    jsonEntity = jsonArray;
                } else {
                    JsonObject jsonObject = new JsonObject();
                    List<ExcludeStrategy> excludeStrategies = this.excludeStrategies;
                    SerializedObject annotation = obj.getClass().getAnnotation(SerializedObject.class);
                    Class<?>[] excludeClasses = new Class[0];

                    if (annotation != null) {
                        SerializedObject.ConsiderArrayType considerArrayType = annotation.writeArraysSingleLined();
                        if (considerArrayType != SerializedObject.ConsiderArrayType.IGNORE) {

                            boolean changed = considerArrayType == SerializedObject.ConsiderArrayType.OVERRIDE_TRUE;
                            //changing setting in instance

                            CHANGED_WRITE_SINGLE_LINE = true;
                            writeArraysSingleLined = changed;
                            SimpleProvider.getInstance().setSerializerModule(this);
                        }
                        excludeClasses = annotation.excludeClasses();
                        maxTry = annotation.serializeSameFieldInstance();

                        Class<? extends ExcludeStrategy> strategy = annotation.strategy();

                        if (strategy != ExcludeStrategy.class) {
                            excludeStrategies.clear();
                            excludeStrategies.add(strategy.newInstance());
                        }
                    }


                    if (obj.getClass().getDeclaredFields().length == 0) {
                        jsonEntity = new JsonString(obj.getClass().getName());
                    } else {
                        for (Field declaredField : obj.getClass().getDeclaredFields()) {
                            declaredField.setAccessible(true);

                            String name = declaredField.getName();
                            Class<?> type = declaredField.getType();
                            Object fieldObject = declaredField.get(obj);
                            boolean ignore = false;
                            SerializedField serializedField = declaredField.getAnnotation(SerializedField.class);

                            if (serializedField != null) {
                                if (!serializedField.name().trim().isEmpty()) {
                                    name = serializedField.name();
                                }

                                for (WrapperClass wrapperClass : serializedField.wrapperClasses()) {
                                    if (type.equals(wrapperClass.interfaceClass())) {
                                        type = wrapperClass.value();
                                    }
                                }

                                ignore = serializedField.ignore();
                            }

                            if (Arrays.asList(excludeClasses).contains(type) || ignore) {
                                continue;
                            }

                            boolean cont = false;

                            for (ExcludeStrategy excludeStrategy : excludeStrategies) {
                                if (excludeStrategy.shouldSkipField(declaredField) || excludeStrategy.shouldSkipClass(type)) {
                                    cont = true;
                                }
                            }

                            if (cont) {
                                continue;
                            }

                            JsonSerializer adapterOrNull = getSerializerOrNull(this, type);
                            if (adapterOrNull != null) {
                                jsonObject.addProperty(name, adapterOrNull.serialize(fieldObject, this, declaredField));
                            } else {
                                if (fieldObject == null) {
                                    if (serializeNulls) {
                                        jsonObject.addProperty(name, JsonLiteral.NULL);
                                    }
                                } else {

                                    JsonEntity entity = JsonEntity.valueOf(fieldObject);

                                    if (entity == null) {
                                        if (type.equals(obj.getClass())) {
                                            if (currentTry != -1 && (currentTry > this.serializeSameFieldInstance)) {
                                                if (serializeNulls) {
                                                    jsonObject.addProperty(name, JsonLiteral.NULL);
                                                }
                                            } else {
                                                jsonObject.addProperty(name, this.toJson(fieldObject, (currentTry + 1), maxTry));
                                            }
                                        } else {
                                            jsonObject.addProperty(name, this.toJson(fieldObject, (currentTry + 1), maxTry));
                                        }
                                    } else {
                                        jsonObject.addProperty(name, entity);
                                    }

                                }
                            }

                        }
                        jsonEntity = jsonObject;
                    }
                }
            }
            jsonEntity.setFormat(this.format);

            if (CHANGED_JSON_FORMAT) this.format = OLD_JSON_FORMAT;
            return jsonEntity;
        } catch (Exception e) {
            if (CHANGED_JSON_FORMAT) this.format = OLD_JSON_FORMAT;
            return null;
        }
    }

    @Override
    public <T> JsonEntity toJson(T obj) {
        return this.toJson(obj, 0, this.serializeSameFieldInstance);
    }

    @Override
    public <T> T fromJson(String json, Class<T> typeClass, Class<?>... argument) {
        ParserModule parserModule = SimpleProvider.getInstance().getParserModule();
        return fromJson(parserModule.parse(json), typeClass, argument);
    }

    @Override
    public <T> T fromJson(Reader reader, Class<T> typeClass, Class<?>... arguments) {
        ParserModule parserModule = SimpleProvider.getInstance().getParserModule();
        return fromJson(parserModule.parse(reader), typeClass, arguments);
    }

    /**
     * Init safety static factory method; instance of this
     * class is published when creating new config sections
     * via the load method in {@link eu.simplejson.config.JsonSection}
     *
     * @param file the file to config
     * @return the loaded config
     */
    @Override
    public JsonConfig loadConfig(File file) {
        return SimpleJsonConfig.CACHE.computeIfAbsent(file.toPath(), k -> {
            SimpleJsonConfig config = new SimpleJsonConfig(file.toPath());
            config.load();

            return config;
        });
    }

    Map<Class<?>, Class<?>> classCache = new HashMap<>();

    @Override
    @SneakyThrows
    public <T> T fromJson(JsonEntity json, Class<T> typeClass, Class<?>... arguments) {
        if (typeClass.isAnnotationPresent(WrapperClass.class)) {
            WrapperClass annotation = typeClass.getAnnotation(WrapperClass.class);
            typeClass = (Class<T>) annotation.value();
        }
        //Trying to get from serializer
        T object = getSerializedOrNull(this, json, typeClass, null, arguments);

        //No serializer... trying with empty object
        if (object == null) {
            if (typeClass.isArray()) {

                //must be arrayed
                List<T> typeList = new ArrayList<>();

                if (!(json instanceof JsonArray)) {
                    throw new RuntimeException();
                }
                List<T> list = new ArrayList<>();
                for (JsonEntity entity : ((JsonArray) json)) {
                    Object obj = entity.asObject();

                    if (obj == null) {
                        String className = typeClass.getSimpleName().split("\\[]")[0];
                        Class<?> aClass;
                        if (classCache.containsKey(typeClass)) {
                            aClass = classCache.get(typeClass);
                        } else {
                            aClass = Class.forName(new Reflections("", new SubTypesScanner(false)).getAllTypes().stream()
                                .filter(o -> o.endsWith("." + className))
                                .findFirst()
                                .orElse(null));
                            classCache.put(typeClass, aClass);
                        }

                        System.out.println(aClass);
                        obj = fromJson(entity, aClass);
                    }

                    list.add((T) obj);
                }
                return (T) typeList.toArray(new Object[0]);
            } else {
                object = JsonHelper.createEmptyObject(typeClass);
            }
        } else {
            return object;
        }

        //Object still null returning null
        if (object == null) {
            return null;
        }
        List<ExcludeStrategy> excludeStrategies = this.excludeStrategies;
        SerializedObject annotation = object.getClass().getAnnotation(SerializedObject.class);
        Class<?>[] excludeClasses = new Class[0];

        if (annotation != null) {
            excludeClasses = annotation.excludeClasses();
            Class<? extends ExcludeStrategy> strategy = annotation.strategy();
            if (strategy != ExcludeStrategy.class) {
                excludeStrategies.clear();
                excludeStrategies.add(strategy.newInstance());
            }
        }

        if (json instanceof JsonObject) {
            JsonObject jsonObject = (JsonObject) json;

            for (String key : jsonObject.keySet()) {
                Field declaredField;
                try {
                    declaredField = object.getClass().getDeclaredField(key);
                } catch (NoSuchFieldException e) {
                    declaredField = Arrays.stream(object.getClass().getDeclaredFields()).filter(field -> field.getAnnotation(SerializedField.class) != null && field.getAnnotation(SerializedField.class).name().equalsIgnoreCase(key)).findFirst().orElse(null);
                }

                if (declaredField == null) {
                    continue;
                }

                declaredField.setAccessible(true);

                String name = declaredField.getName();
                Class<?> type = declaredField.getType();
                boolean ignore = false;
                SerializedField serializedField = declaredField.getAnnotation(SerializedField.class);

                if (serializedField != null) {
                    if (!serializedField.name().trim().isEmpty()) {
                        name = serializedField.name();
                    }

                    for (WrapperClass wrapperClass : serializedField.wrapperClasses()) {
                        if (type.equals(wrapperClass.interfaceClass())) {
                            type = wrapperClass.value();
                        }
                    }

                    ignore = serializedField.ignore();
                }

                if (Arrays.asList(excludeClasses).contains(type) || ignore) {
                    continue;
                }

                boolean cont = false;
                for (ExcludeStrategy excludeStrategy : excludeStrategies) {
                    if (excludeStrategy.shouldSkipField(declaredField) || excludeStrategy.shouldSkipClass(type)) {
                        cont = true;
                    }
                }
                if (cont) {
                    continue;
                }

                JsonEntity jsonEntity = jsonObject.get(name);
                Object value = getSerializedOrNull(this, jsonEntity, type, declaredField);

                if (type.isPrimitive() && value == null) {
                    Class<?> wrapperClassForPrimitive = JsonHelper.getWrapperClassForPrimitive(type);
                    value = getSerializedOrNull(this, jsonEntity, wrapperClassForPrimitive, declaredField);
                    if (value == null) {
                        continue;
                    }
                } else if (value == null) {
                    value = fromJson(jsonEntity, type);
                }

                declaredField.set(object, value);
            }
        } else if (json instanceof JsonLiteral) {
            JsonLiteral jsonLiteral = (JsonLiteral) json;
            if (jsonLiteral == JsonLiteral.TRUE && (typeClass == Boolean.class || typeClass == boolean.class)) {
                object = (T) Boolean.TRUE;
            } else if (jsonLiteral == JsonLiteral.FALSE && (typeClass == Boolean.class || typeClass == boolean.class)) {
                object = (T) Boolean.FALSE;
            } else if (jsonLiteral == JsonLiteral.NULL) {
                object = null;
            }
        } else if (json instanceof JsonString) {
            object = (T) json.asString();
        } else if (json instanceof JsonArray) {
            List<T> list = new ArrayList<>();
            for (JsonEntity entity : ((JsonArray) json)) {
                Object obj = entity.asObject();

                if (obj == null) {
                    continue;
                }

                list.add((T) obj);
                System.out.println(entity);
                //TODO
            }
            object = (T) list;
        } else if (json instanceof JsonNumber) {
            JsonNumber jsonNumber = (JsonNumber) json;
            object = (T) jsonNumber.asObject();
        } else {
            System.out.println("Json could not deserialize Entity of type " + json.jsonType() + "!");
        }
        return object;
    }

    /**
     * Searches for all {@link JsonSerializer}s that match a given {@link Class}
     * If checkSerializersForSubClasses is enabled it will scan for all sub-classes
     * if the main-class does not have any serializer registered
     *
     * @param typeClass the type class
     * @param <T>       the generic
     * @return serializer or null
     */
    public static <T> JsonSerializer<T> getSerializerOrNull(Json json, Class<T> typeClass) {
        JsonSerializer<T> jsonSerializer = null;
        List<Class<?>> subClasses = JsonHelper.loadAllSubClasses(typeClass);
        if (json.getRegisteredSerializers().containsKey(typeClass)) {
            jsonSerializer = (JsonSerializer<T>) json.getRegisteredSerializers().get(typeClass);
            if (jsonSerializer == null) {
                if (json.isCheckSerializersForSubClasses()) {
                    for (Class<?> aClass : JsonHelper.loadAllSubClasses(typeClass)) {
                        jsonSerializer = (JsonSerializer<T>) getSerializerOrNull(json, aClass);
                        break;
                    }
                }
            } else {
                return jsonSerializer;
            }
        } else {
            for (Class<?> subClass : subClasses) {
                if (json.getRegisteredSerializers().containsKey(subClass)) {
                    jsonSerializer = (JsonSerializer<T>) getSerializerOrNull(json, subClass);
                }
            }
        }
        return jsonSerializer;
    }

    /**
     * Gets an object from a given {@link JsonEntity} if its adapter
     * exists, otherwise it will just return null
     *
     * @param json      the entity
     * @param typeClass the type class
     * @param <T>       the generic
     * @return object or null
     */
    public static <T> T getSerializedOrNull(Json instance, JsonEntity json, Class<T> typeClass, Field field, Class<?>... arguments) {
        JsonSerializer<T> adapterOrNull = getSerializerOrNull(instance, typeClass);
        return adapterOrNull == null ? null : adapterOrNull.deserialize(json, field, instance, arguments);
    }

}
