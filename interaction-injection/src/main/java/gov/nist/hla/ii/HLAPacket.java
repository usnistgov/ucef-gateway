package gov.nist.hla.ii;

import java.util.Map;

public class HLAPacket {
    public enum TYPE {
        INTERACTION, OBJECT
    };

    public final TYPE type;

    protected final String className;
    protected final Map<String, String> fields;

    public HLAPacket(String className, Map<String, String> fields, TYPE type) {
        this.className = className;
        this.fields = fields;
        this.type = type;
    }

    public String getClassName() {
        return className;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public TYPE getType() {
        return type;
    }

    public String toString() {
        return String.format("name=%s fields=%d type=%s", className, fields.size(), type.name());
    }

    public boolean isInteraction() {
        return type == TYPE.INTERACTION;
    }

    public boolean isObject() {
        return type == TYPE.OBJECT;
    }
}
