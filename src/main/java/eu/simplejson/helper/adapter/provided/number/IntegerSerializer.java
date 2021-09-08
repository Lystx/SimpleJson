package eu.simplejson.helper.adapter.provided.number;

import eu.simplejson.JsonEntity;
import eu.simplejson.helper.adapter.JsonSerializer;
import eu.simplejson.helper.Json;

public class IntegerSerializer extends JsonSerializer<Integer> {

    @Override
    public Integer deserialize(JsonEntity element) {

        return element.asInt();
    }

    @Override
    public JsonEntity serialize(Integer obj,Json json) {
        return JsonEntity.valueOf(obj);
    }
}
