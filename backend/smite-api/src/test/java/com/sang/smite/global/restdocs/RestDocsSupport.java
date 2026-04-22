package com.sang.smite.global.restdocs;

import com.sang.smite.global.exception.GlobalExceptionHandler;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import io.restassured.module.mockmvc.specification.MockMvcRequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;

@ExtendWith(RestDocumentationExtension.class)
public abstract class RestDocsSupport {

    protected MockMvcRequestSpecification spec;

    @BeforeEach
    void setUp(RestDocumentationContextProvider provider) {
        this.spec = RestAssuredMockMvc.given()
                .mockMvc(MockMvcBuilders.standaloneSetup(initController())
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .apply(documentationConfiguration(provider)
                                .operationPreprocessors()
                                .withRequestDefaults(prettyPrint())
                                .withResponseDefaults(prettyPrint()))
                        .build());
    }

    protected abstract Object initController();
}
