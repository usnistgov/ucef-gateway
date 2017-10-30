package gov.nist.hla.ii;

import java.util.Map;

public class HLAPacket {
    public enum TYPE {
        INTERACTION, OBJECT
    };

    public final TYPE type;

    protected final int handle;
    protected final String name;
    protected final Map<String, String> fields;

    public HLAPacket(int handle, Map<String, String> fields, TYPE type) {
        this.name = null;
        this.handle = handle;
        this.fields = fields;
        this.type = type;
    }
    
    public HLAPacket(String name, Map<String, String> fields, TYPE type) {
        this.handle = 0;
        this.name = name;
        this.fields = fields;
        this.type = type;
    }
    
    public int getHandle() {
        return handle;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public TYPE getType() {
        return type;
    }

    public String toString() {
        return String.format("name=%s fields=%d type=%s", name, fields.size(), type.name());
    }

    public boolean isHandleDefined() {
        return name == null;
    }
    
    public boolean isInteraction() {
        return type == TYPE.INTERACTION;
    }

    public boolean isObject() {
        return type == TYPE.OBJECT;
    }
}
