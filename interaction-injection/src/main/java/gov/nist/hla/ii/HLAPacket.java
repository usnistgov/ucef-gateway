package gov.nist.hla.ii;

import java.util.Map;

public class HLAPacket {
    public enum TYPE {
        INTERACTION, OBJECT
    };

    protected final TYPE type;
    protected final String name;
    protected final Map<String, String> fields;
    
    public HLAPacket(String name, Map<String, String> fields, TYPE type) {
        this.name = name;
        this.fields = fields;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getFields() {
        return fields;
    }
    
    public boolean isInteraction() {
        return type == TYPE.INTERACTION;
    }

    public boolean isObject() {
        return type == TYPE.OBJECT;
    }
    
    public String toString() {
        return String.format("name=%s fields=%d type=%s", name, fields.size(), type.name());
    }
}
