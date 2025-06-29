package com.app.categorise.config;

import com.app.categorise.api.dto.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiGlobalErrorConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        Components components = new Components();

        // Automatically register schema using ModelConverters
        Schema<?> errorSchema = ModelConverters.getInstance().read(ErrorResponse.class).get("ErrorResponse");

        components.addSchemas("ErrorResponse", errorSchema);

        components.addResponses("BadRequest", new ApiResponse()
                .description("Bad Request")
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))));

        components.addResponses("Forbidden", new ApiResponse()
                .description("Access Denied")
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))));

        components.addResponses("InternalError", new ApiResponse()
                .description("Internal Server Error")
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))));

        return new OpenAPI().components(components);
    }

    @Bean
    public OpenApiCustomizer globalErrorResponsesCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
            pathItem.readOperations().forEach(operation -> {
                ApiResponses responses = operation.getResponses();

                if (!responses.containsKey("400")) {
                    responses.addApiResponse("400", new ApiResponse().$ref("#/components/responses/BadRequest"));
                }
                if (!responses.containsKey("403")) {
                    responses.addApiResponse("403", new ApiResponse().$ref("#/components/responses/Forbidden"));
                }
                if (!responses.containsKey("500")) {
                    responses.addApiResponse("500", new ApiResponse().$ref("#/components/responses/InternalError"));
                }
            });
        });
    }
}
