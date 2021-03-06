package io.swagger.parser;

import io.swagger.models.*;
import io.swagger.models.parameters.*;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.MapProperty;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.properties.StringProperty;
import io.swagger.parser.util.SwaggerDeserializationResult;
import io.swagger.util.Json;
import io.swagger.util.Yaml;

import org.junit.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;


public class SwaggerParserTest {

    @Test
    public void testLoadRelativeFileTree_Json() throws Exception {
        final Swagger swagger = doRelativeFileTest("src/test/resources/relative-file-references/json/parent.json");
        //Json.mapper().writerWithDefaultPrettyPrinter().writeValue(new File("resolved.json"), swagger);
    }

    @Test
    public void testPetstore() throws Exception {
        SwaggerParser parser = new SwaggerParser();
        SwaggerDeserializationResult result = parser.readWithInfo("src/test/resources/petstore.json", null, true);

        assertNotNull(result);
    }

    @Test
    public void testTroublesomeFile() throws Exception {
        SwaggerParser parser = new SwaggerParser();
        final Swagger swagger = parser.read("src/test/resources/troublesome.yaml");
    }

    @Test
    public void testLoadRelativeFileTree_Yaml() throws Exception {
        JsonToYamlFileDuplicator.duplicateFilesInYamlFormat("src/test/resources/relative-file-references/json",
                "src/test/resources/relative-file-references/yaml");
        final Swagger swagger = doRelativeFileTest("src/test/resources/relative-file-references/yaml/parent.yaml");
        assertNotNull(Yaml.mapper().writeValueAsString(swagger));
    }

    @Test
    public void testIssue75() {
        SwaggerParser parser = new SwaggerParser();
        final Swagger swagger = parser.read("src/test/resources/issue99.json");

        BodyParameter param = (BodyParameter)swagger.getPaths().get("/albums").getPost().getParameters().get(0);
        Model model = param.getSchema();

        assertNotNull(model);
        assertTrue(model instanceof ArrayModel);

        ArrayModel am = (ArrayModel) model;
        assertTrue(am.getItems() instanceof StringProperty);
        assertEquals(am.getItems().getFormat(), "byte");
    }

    @Test
    public void testIssue62() {
        SwaggerParser parser = new SwaggerParser();
        final Swagger swagger = parser.read("https://raw.githubusercontent.com/swagger-api/swagger-spec/master/fixtures/v2.0/json/resources/resourceWithLinkedDefinitions.json");

        assertNotNull(swagger.getPaths().get("/pets/{petId}").getGet());
    }
    
    @Test
    public void testParameterRequired() {
        SwaggerParser parser = new SwaggerParser();
        final Swagger swagger = parser.read("src/test/resources/petstore.json");
        final List<Parameter> operationParams = swagger.getPath("/pet/{petId}").getPost().getParameters();
        
        final PathParameter pathParameter = (PathParameter) operationParams.get(0);
        Assert.assertTrue(pathParameter.getRequired());
        
        final FormParameter formParameter = (FormParameter) operationParams.get(1);
        Assert.assertFalse(formParameter.getRequired());        
    }

    @Test
    public void testIssue108() {
        SwaggerParser parser = new SwaggerParser();
        final Swagger swagger = parser.read("src/test/resources/issue_108.json");

        assertNotNull(swagger);
    }

    private Swagger doRelativeFileTest(String location) {
        SwaggerParser parser = new SwaggerParser();
        SwaggerDeserializationResult readResult = parser.readWithInfo(location, null, true);
        if(readResult.getMessages().size() > 0) {
            Json.prettyPrint(readResult.getMessages());
        }
        final Swagger swagger = readResult.getSwagger();

        final Path path = swagger.getPath("/health");
        assertEquals(path.getClass(), Path.class); //we successfully converted the RefPath to a Path

        final List<Parameter> parameters = path.getParameters();
        assertParamDetails(parameters, 0, QueryParameter.class, "param1", "query");
        assertParamDetails(parameters, 1, HeaderParameter.class, "param2", "header");

        final Operation operation = path.getGet();
        final List<Parameter> operationParams = operation.getParameters();
        assertParamDetails(operationParams, 0, PathParameter.class, "param3", "path");
        assertParamDetails(operationParams, 1, HeaderParameter.class, "param4", "header");
        assertParamDetails(operationParams, 2, BodyParameter.class, "body", "body");
        final BodyParameter bodyParameter = (BodyParameter) operationParams.get(2);
        assertEquals(((RefModel) bodyParameter.getSchema()).get$ref(), "#/definitions/health");

        final Map<String, Response> responsesMap = operation.getResponses();

        assertResponse(swagger, responsesMap, "200", "Health information from the server", "#/definitions/health");
        assertResponse(swagger, responsesMap, "400", "Your request was not valid", "#/definitions/error");
        assertResponse(swagger, responsesMap, "500", "An unexpected error occur during processing", "#/definitions/error");

        final Map<String, Model> definitions = swagger.getDefinitions();
        final ModelImpl refInDefinitions = (ModelImpl) definitions.get("refInDefinitions");
        assertEquals(refInDefinitions.getDescription(), "The example model");
        expectedPropertiesInModel(refInDefinitions, "foo", "bar");

        final ArrayModel arrayModel = (ArrayModel) definitions.get("arrayModel");
        final RefProperty arrayModelItems = (RefProperty) arrayModel.getItems();
        assertEquals(arrayModelItems.get$ref(), "#/definitions/foo");

        final ModelImpl fooModel = (ModelImpl) definitions.get("foo");
        assertEquals(fooModel.getDescription(), "Just another model");
        expectedPropertiesInModel(fooModel, "hello", "world");

        final ComposedModel composedCat = (ComposedModel) definitions.get("composedCat");
        final ModelImpl child = (ModelImpl) composedCat.getChild();
        expectedPropertiesInModel(child, "huntingSkill", "prop2", "reflexes", "reflexMap");
        final ArrayProperty reflexes = (ArrayProperty) child.getProperties().get("reflexes");
        final RefProperty reflexItems = (RefProperty) reflexes.getItems();
        assertEquals(reflexItems.get$ref(), "#/definitions/reflex");
        assertTrue(definitions.containsKey(reflexItems.getSimpleRef()));

        final MapProperty reflexMap = (MapProperty) child.getProperties().get("reflexMap");
        final RefProperty reflexMapAdditionalProperties = (RefProperty) reflexMap.getAdditionalProperties();
        assertEquals(reflexMapAdditionalProperties.get$ref(), "#/definitions/reflex");

        final RefModel parent = (RefModel) composedCat.getParent();
        assertEquals(parent.get$ref(), "#/definitions/pet");

        assertEquals(composedCat.getInterfaces().size(), 1);
        assertEquals(composedCat.getInterfaces().get(0).get$ref(), "#/definitions/foo1");

        return swagger;
    }

    private void expectedPropertiesInModel(ModelImpl model, String... expectedProperties) {
        assertEquals(model.getProperties().size(), expectedProperties.length);
        for (String expectedProperty : expectedProperties) {
            assertTrue(model.getProperties().containsKey(expectedProperty));
        }
    }

    private void assertResponse(Swagger swagger, Map<String, Response> responsesMap, String responseCode,
                                String expectedDescription, String expectedSchemaRef) {
        final Response response = responsesMap.get(responseCode);
        final RefProperty schema = (RefProperty) response.getSchema();
        assertEquals(response.getDescription(), expectedDescription);
        assertEquals(schema.getClass(), RefProperty.class);
        assertEquals(schema.get$ref(), expectedSchemaRef);
        assertTrue(swagger.getDefinitions().containsKey(schema.getSimpleRef()));
    }

    private void assertParamDetails(List<Parameter> parameters, int index, Class<?> expectedType,
                                    String expectedName, String expectedIn) {
        final Parameter param1 = parameters.get(index);
        assertEquals(param1.getClass(), expectedType);
        assertEquals(param1.getName(), expectedName);
        assertEquals(param1.getIn(), expectedIn);
    }
}
