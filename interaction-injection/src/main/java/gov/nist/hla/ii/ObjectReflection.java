package gov.nist.hla.ii;

import java.util.ArrayList;

import hla.rti.ArrayIndexOutOfBounds;
import hla.rti.ReflectedAttributes;

public class ObjectReflection {
    private class Attribute {
        private int handle;
        private String value;

        public Attribute(int handle, String value) {
            this.handle = handle;
            this.value = value;
        }

        public int getHandle() {
            return handle;
        }

        public String getValue() {
            return value;
        }
    }

    private int objectClass;
    private String objectName;
    private ArrayList<Attribute> attributes;

    public ObjectReflection(int objectClass, String objectName, ReflectedAttributes theAttributes) {
        this.objectClass = objectClass;
        this.objectName = objectName;
        this.attributes = new ArrayList<Attribute>(theAttributes.size());

        for (int i = 0; i < theAttributes.size(); i++) {
            try {
                int attributeHandle = theAttributes.getAttributeHandle(i);
                String attributeValue = decodeString(theAttributes.getValue(i));
                attributes.add(new Attribute(attributeHandle, attributeValue));
            } catch (ArrayIndexOutOfBounds e) {
                throw new IndexOutOfBoundsException(e.getMessage()); // unreachable code
            }
        }
    }

    public int getObjectClass() {
        return objectClass;
    }

    public String getObjectName() {
        return objectName;
    }

    public int getAttributeCount() {
        return attributes.size();
    }

    public int getAttributeHandle(int index) {
        return attributes.get(index).getHandle();
    }

    public String getAttributeValue(int index) {
        return attributes.get(index).getValue();
    }

    private String decodeString(byte[] buffer) {
        String result = new String(buffer);
        if (result.endsWith("\0")) {
            // the MOM implementation in Portico terminates strings with \0
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
    
    public String toString() {
        return String.format("object name=%s class=%d attributes=%d", objectName, objectClass, attributes.size());
    }
}
