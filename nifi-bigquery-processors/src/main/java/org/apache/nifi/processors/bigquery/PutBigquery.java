package org.apache.nifi.processors.bigquery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import org.apache.nifi.annotation.behavior.SupportsBatching;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.PropertyValue;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.processors.bigquery.utils.NotNullValuesHashMap;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@SupportsBatching
@Tags({"Google", "BigQuery", "Google Cloud", "Put", "Insert"})
@CapabilityDescription("Puts a JSON document as a row into a BigQuery Table. The JSON fields are mapped with the table's columns names."
        + "If a JSON field not match with a table's column name, it will be ignored."
        + " The FlowFile content must be JSON")
public class PutBigquery extends AbstractBigqueryProcessor {

    static final PropertyDescriptor TABLE = new PropertyDescriptor.Builder()
            .name("Bigquery Table")
            .description("The table id where store the data. The table must be exist on bigquery")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    static final PropertyDescriptor DATASET = new PropertyDescriptor.Builder()
            .name("Bigquery Dataset")
            .description("The dataset id where find the table. The dataset must be exist on bigquery")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    static final PropertyDescriptor OMIT_FIELDS = new PropertyDescriptor.Builder()
            .name("Fields to omit in json")
            .description("The list of the fields that will be removed from json. These fields should not be listed on bigquery. Ex:field_name1,field_name2")
            .required(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final List<PropertyDescriptor> properties = Collections.unmodifiableList(
            Arrays.asList(SERVICE_ACCOUNT_CREDENTIALS_JSON, READ_TIMEOUT, CONNECTION_TIMEOUT, PROJECT, DATASET, TABLE, OMIT_FIELDS));


    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return properties;
    }


    @Override
    public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
        FlowFile flowFile = session.get();
        final String table = context.getProperty(TABLE).getValue();
        final String dataset = context.getProperty(DATASET).getValue();
        PropertyValue omitFieldsProperty = context.getProperty(OMIT_FIELDS);

        ObjectMapper mapper = new ObjectMapper();
        try {
            Map<String, Object> jsonDocument = mapper.readValue(session.read(flowFile), new TypeReference<NotNullValuesHashMap<String, Object>>() {
            });

            if (omitFieldsProperty.isSet()) { // if we have any fields that will be removed from the json record :
                for (String omit_key : omitFieldsProperty.getValue().split(",")) {
                    if (jsonDocument.containsKey(omit_key))
                        jsonDocument.remove(omit_key);
                }
            }

            InsertAllRequest.RowToInsert rowToInsert = InsertAllRequest.RowToInsert.of(jsonDocument);
            BigQuery bigQuery = getBigQuery();

            InsertAllRequest insertAllRequest = InsertAllRequest.of(dataset, table, rowToInsert);


            InsertAllResponse insertAllResponse = bigQuery.insertAll(insertAllRequest);

            if (insertAllResponse.hasErrors()) {
                session.transfer(flowFile, REL_FAILURE);
            } else {
                session.transfer(flowFile, REL_SUCCESS);
            }


        } catch (IOException ioe) {
            getLogger().error("IOException while reading JSON item: " + ioe.getMessage());
            session.transfer(flowFile, REL_FAILURE);
        }


    }
}
