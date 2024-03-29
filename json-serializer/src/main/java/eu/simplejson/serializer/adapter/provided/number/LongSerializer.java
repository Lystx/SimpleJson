package eu.simplejson.serializer.adapter.provided.number;

import eu.simplejson.serializer.adapter.JsonSerializer;

import eu.simplejson.elements.JsonEntity;
import eu.simplejson.serializer.Json;

import java.lang.reflect.Field;

public class LongSerializer extends JsonSerializer<Long> {

    @Override
    public Long deserialize(JsonEntity element, Field field, Json json, Class<?>... arguments) {
        return element.asLong();
    }

    @Override
    public JsonEntity serialize(Long obj, Json json, Field field) {
        return JsonEntity.valueOf(obj);
    }
}
