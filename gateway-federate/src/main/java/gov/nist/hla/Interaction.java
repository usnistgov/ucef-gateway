package gov.nist.hla;

import java.util.ArrayList;
import java.util.List;

import hla.rti.ArrayIndexOutOfBounds;
import hla.rti.ReceivedInteraction;

public class Interaction {
    private class Parameter {
        private int handle;
        private String value;

        public Parameter(int handle, String value) {
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

    private int classHandle;
    private List<Parameter> parameters;

    public Interaction(int interactionClass, ReceivedInteraction theInteraction) {
        this.classHandle = interactionClass;
        this.parameters = new ArrayList<Parameter>(theInteraction.size());

        for (int i = 0; i < theInteraction.size(); i++) {
            try {
                int parameterHandle = theInteraction.getParameterHandle(i);
                String parameterValue = decodeString(theInteraction.getValue(i));
                parameters.add(new Parameter(parameterHandle, parameterValue));
            } catch (ArrayIndexOutOfBounds e) {
                throw new IndexOutOfBoundsException(e.getMessage()); // unreachable code
            }
        }
    }

    public int getClassHandle() {
        return classHandle;
    }

    public int getParameterCount() {
        return parameters.size();
    }

    public int getParameterHandle(int index) {
        return parameters.get(index).getHandle();
    }

    public String getParameterValue(int index) {
        return parameters.get(index).getValue();
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
        return String.format("interaction class=%d parameters=%d", classHandle, parameters.size());
    }
}
