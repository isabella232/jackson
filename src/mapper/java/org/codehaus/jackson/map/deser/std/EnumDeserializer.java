package org.codehaus.jackson.map.deser.std;

import java.io.IOException;
import java.lang.reflect.Method;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.JsonDeserializer;
import org.codehaus.jackson.map.DeserializationContext;
import org.codehaus.jackson.map.annotate.JsonCachable;
import org.codehaus.jackson.map.introspect.AnnotatedMethod;
import org.codehaus.jackson.map.util.ClassUtil;
import org.codehaus.jackson.map.util.EnumResolver;

/**
 * Deserializer class that can deserialize instances of
 * specified Enum class from Strings and Integers.
 * 
 * @since 1.9 (moved from higher-level package)
 */
@JsonCachable
public class EnumDeserializer
    extends StdScalarDeserializer<Enum<?>>
{
    protected final EnumResolver<?> _resolver;
    
    public EnumDeserializer(EnumResolver<?> res)
    {
        super(Enum.class);
        _resolver = res;
    }

    /**
     * Factory method used when Enum instances are to be deserialized
     * using a creator (static factory method)
     * 
     * @return Deserializer based on given factory method, if type was suitable;
     *  null if type can not be used
     */
    public static JsonDeserializer<?> deserializerForCreator(DeserializationConfig config,
            Class<?> enumClass, AnnotatedMethod factory)
    {
        // note: caller has verified there's just one arg; but we must verify its type
        Class<?> raw = factory.getParameterClass(0);
        if (raw == String.class) {
            raw = null;
        } else  if (raw == Integer.TYPE || raw == Integer.class) {
            raw = Integer.class;
        } else  if (raw == Long.TYPE || raw == Long.class) {
            raw = Long.class;
        } else {
            throw new IllegalArgumentException("Parameter #0 type for factory method ("+factory
                    +") not suitable, must be java.lang.String or int/Integer/long/Long");
        }
        if (config.isEnabled(DeserializationConfig.Feature.CAN_OVERRIDE_ACCESS_MODIFIERS)) {
            ClassUtil.checkAndFixAccess(factory.getMember());
        }
        return new FactoryBasedDeserializer(enumClass, factory, raw);
    }
    
    /*
    /**********************************************************
    /* Default JsonDeserializer implementation
    /**********************************************************
     */

    @Override
    public Enum<?> deserialize(JsonParser jp, DeserializationContext ctxt)
        throws IOException, JsonProcessingException
    {
        JsonToken curr = jp.getCurrentToken();
        
        // Usually should just get string value; but in some cases FIELD_NAME (enum as key)
        if (curr == JsonToken.VALUE_STRING || curr == JsonToken.FIELD_NAME) {
            String name = jp.getText();
            Enum<?> result = _resolver.findEnum(name);
            if (result == null) {
                throw ctxt.weirdStringException(_resolver.getEnumClass(), "value not one of declared Enum instance names");
            }
            return result;
        }
        // But let's consider int acceptable as well (if within ordinal range)
        if (curr == JsonToken.VALUE_NUMBER_INT) {
            /* ... unless told not to do that. :-)
             * (as per [JACKSON-412]
             */
            if (ctxt.isEnabled(DeserializationConfig.Feature.FAIL_ON_NUMBERS_FOR_ENUMS)) {
                throw ctxt.mappingException("Not allowed to deserialize Enum value out of JSON number (disable DeserializationConfig.Feature.FAIL_ON_NUMBERS_FOR_ENUMS to allow)");
            }
            
            int index = jp.getIntValue();
            Enum<?> result = _resolver.getEnum(index);
            if (result == null) {
                throw ctxt.weirdNumberException(_resolver.getEnumClass(), "index value outside legal index range [0.."+_resolver.lastValidIndex()+"]");
            }
            return result;
        }
        throw ctxt.mappingException(_resolver.getEnumClass());
    }

    /*
    /**********************************************************
    /* Default JsonDeserializer implementation
    /**********************************************************
     */

    /**
     * Deserializer that uses a single-String static factory method
     * for locating Enum values by String id.
     */
    protected static class FactoryBasedDeserializer
        extends StdScalarDeserializer<Object>
    {
        protected final Class<?> _enumClass;

        // Marker type; null if String expected; otherwise numeric wrapper
        protected final Class<?> _inputType;
        protected final Method _factory;
        
        public FactoryBasedDeserializer(Class<?> cls, AnnotatedMethod f,
                Class<?> inputType)
        {
            super(Enum.class);
            _enumClass = cls;
            _factory = f.getAnnotated();
            _inputType = inputType;
        }

        @Override
        public Object deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException
        {
            // couple of accepted types...
            Object value;
            if (_inputType == null) {
                value = jp.getText();
            } else  if (_inputType == Integer.class) {
                value = Integer.valueOf(jp.getValueAsInt());
            } else  if (_inputType == Long.class) {
                value = Long.valueOf(jp.getValueAsLong());
            } else {
                throw ctxt.mappingException(_enumClass);
            }
            try {
                return _factory.invoke(_enumClass, value);
            } catch (Exception e) {
                ClassUtil.unwrapAndThrowAsIAE(e);
            }
            return null;
        }
    }
}
