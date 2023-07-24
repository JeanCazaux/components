package org.talend.components.common.avro;

import org.apache.avro.AvroTypeException;
import org.apache.avro.Schema;

public class AvroTool {

    public static Schema.Field cloneAvroField(Schema.Field origin) {
        try {
            return new Schema.Field(origin.name(), origin.schema(), origin.doc(), origin.defaultVal());
        } catch(AvroTypeException e) {
            return new Schema.Field(origin.name(), origin.schema(), origin.doc(), null);
        }
    }

    public static Schema.Field cloneAvroFieldWithOrder(Schema.Field origin) {
        try {
            return new Schema.Field(origin.name(), origin.schema(), origin.doc(), origin.defaultVal(), origin.order());
        } catch(AvroTypeException e) {
            return new Schema.Field(origin.name(), origin.schema(), origin.doc(), null, origin.order());
        }
    }

    public static Schema.Field cloneAvroFieldWithCustomSchema(Schema.Field origin, Schema customSchema) {
        try {
            return new Schema.Field(origin.name(), customSchema, origin.doc(), origin.defaultVal());
        } catch(AvroTypeException e) {
            return new Schema.Field(origin.name(), customSchema, origin.doc(), null);
        }
    }

    public static Schema.Field cloneAvroFieldWithCustomSchemaAndOrder(Schema.Field origin, Schema customSchema) {
        try {
            return new Schema.Field(origin.name(), customSchema, origin.doc(), origin.defaultVal(), origin.order());
        } catch(AvroTypeException e) {
            return new Schema.Field(origin.name(), customSchema, origin.doc(), null, origin.order());
        }
    }

    public static Schema.Field cloneAvroFieldWithoutDoc(Schema.Field origin) {
        try {
            return new Schema.Field(origin.name(), origin.schema(), null, origin.defaultVal());
        } catch(AvroTypeException e) {
            return new Schema.Field(origin.name(), origin.schema(),null, null);
        }
    }

    public static Schema.Field cloneAvroFieldWithCustomName(Schema.Field origin, String customName) {
        try {
            return new Schema.Field(customName, origin.schema(), origin.doc(), origin.defaultVal());
        } catch(AvroTypeException e) {
            return new Schema.Field(customName, origin.schema(), origin.doc(), null);
        }
    }

}
