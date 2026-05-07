package pos.pos.unit.tables.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import pos.pos.exception.handler.GlobalExceptionHandler;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.tables.controller.TableCategoryController;
import pos.pos.tables.dto.ReorderTableCategoriesRequest;
import pos.pos.tables.dto.TableCategoryRequest;
import pos.pos.tables.dto.TableCategoryResponse;
import pos.pos.tables.enums.TableLocationType;
import pos.pos.tables.service.TableCategoryService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("TableCategoryController")
class TableCategoryControllerTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000611");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000612");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000613");
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000614");
    private static final UUID SECOND_CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000615");

    @Mock
    private TableCategoryService tableCategoryService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new TableCategoryController(tableCategoryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        authentication = new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(ACTOR_ID)
                        .email("tables.owner@pos.local")
                        .username("tables.owner")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
    }

    @Test
    @DisplayName("POST table category should return 201")
    void shouldCreateTableCategory() throws Exception {
        TableCategoryRequest request = TableCategoryRequest.builder()
                .code("patio")
                .name("Patio")
                .defaultCapacity(4)
                .locationType(TableLocationType.PATIO)
                .color("#22AA66")
                .displayOrder(2)
                .active(true)
                .build();

        given(tableCategoryService.createTableCategory(eq(authentication), eq(RESTAURANT_ID), eq(BRANCH_ID), any(TableCategoryRequest.class)))
                .willReturn(TableCategoryResponse.builder()
                        .id(CATEGORY_ID)
                        .restaurantId(RESTAURANT_ID)
                        .branchId(BRANCH_ID)
                        .code("PATIO")
                        .name("Patio")
                        .defaultCapacity(4)
                        .locationType(TableLocationType.PATIO)
                        .displayOrder(2)
                        .active(true)
                        .build());

        mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/table-categories", RESTAURANT_ID, BRANCH_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(CATEGORY_ID.toString()))
                .andExpect(jsonPath("$.code").value("PATIO"));
    }

    @Test
    @DisplayName("PATCH reorder should return reordered categories")
    void shouldReorderTableCategories() throws Exception {
        ReorderTableCategoriesRequest request = ReorderTableCategoriesRequest.builder()
                .categoryIds(List.of(SECOND_CATEGORY_ID, CATEGORY_ID))
                .build();

        given(tableCategoryService.reorderTableCategories(eq(authentication), eq(RESTAURANT_ID), eq(BRANCH_ID), any(ReorderTableCategoriesRequest.class)))
                .willReturn(List.of(
                        TableCategoryResponse.builder().id(SECOND_CATEGORY_ID).displayOrder(0).name("Patio").build(),
                        TableCategoryResponse.builder().id(CATEGORY_ID).displayOrder(1).name("Main Dining").build()
                ));

        mockMvc.perform(patch("/restaurants/{restaurantId}/branches/{branchId}/table-categories/reorder", RESTAURANT_ID, BRANCH_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(SECOND_CATEGORY_ID.toString()))
                .andExpect(jsonPath("$[0].displayOrder").value(0))
                .andExpect(jsonPath("$[1].id").value(CATEGORY_ID.toString()))
                .andExpect(jsonPath("$[1].displayOrder").value(1));
    }
}
