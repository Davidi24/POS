package pos.pos.unit.menu.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import pos.pos.exception.menu.OptionGroupTypeDeletionBlockedException;
import pos.pos.menu.dto.CreateOptionGroupTypeRequest;
import pos.pos.menu.dto.OptionGroupTypeResponse;
import pos.pos.menu.mapper.MenuMapper;
import pos.pos.menu.repository.OptionGroupRepository;
import pos.pos.menu.repository.OptionGroupTypeRepository;
import pos.pos.menu.service.OptionGroupTypeService;
import pos.pos.menu.entity.OptionGroupType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OptionGroupTypeService")
class OptionGroupTypeServiceTest {

    private static final UUID TYPE_ID = UUID.fromString("00000000-0000-0000-0000-000000000451");

    @Mock
    private OptionGroupTypeRepository optionGroupTypeRepository;

    @Mock
    private OptionGroupRepository optionGroupRepository;

    private OptionGroupTypeService optionGroupTypeService;

    @BeforeEach
    void setUp() {
        optionGroupTypeService = new OptionGroupTypeService(optionGroupTypeRepository, optionGroupRepository, new MenuMapper());
    }

    @Test
    @DisplayName("getTypes should apply search by code or name")
    void shouldSearchTypes() {
        OptionGroupType type = type("SINGLE_SELECT", "Single Select");
        given(optionGroupTypeRepository.searchByCodeOrName("%single%")).willReturn(List.of(type));

        List<OptionGroupTypeResponse> response = optionGroupTypeService.getTypes((Authentication) null, "single");

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getCode()).isEqualTo("SINGLE_SELECT");
    }

    @Test
    @DisplayName("createType should derive code from name and normalize values")
    void shouldCreateType() {
        CreateOptionGroupTypeRequest request = CreateOptionGroupTypeRequest.builder()
                .name(" Multi Select ")
                .description(" Multiple choices ")
                .build();

        given(optionGroupTypeRepository.existsByCode("MULTI_SELECT")).willReturn(false);
        given(optionGroupTypeRepository.existsByName("Multi Select")).willReturn(false);
        given(optionGroupTypeRepository.saveAndFlush(any(OptionGroupType.class))).willAnswer(invocation -> {
            OptionGroupType saved = invocation.getArgument(0);
            saved.setId(TYPE_ID);
            return saved;
        });

        OptionGroupTypeResponse response = optionGroupTypeService.createType(null, request);

        ArgumentCaptor<OptionGroupType> captor = ArgumentCaptor.forClass(OptionGroupType.class);
        verify(optionGroupTypeRepository).saveAndFlush(captor.capture());
        OptionGroupType saved = captor.getValue();

        assertThat(saved.getCode()).isEqualTo("MULTI_SELECT");
        assertThat(saved.getName()).isEqualTo("Multi Select");
        assertThat(saved.getDescription()).isEqualTo("Multiple choices");
        assertThat(response.getId()).isEqualTo(TYPE_ID);
    }

    @Test
    @DisplayName("deleteType should reject types that are still linked to option groups")
    void shouldRejectDeleteWhenLinked() {
        OptionGroupType type = type("SINGLE_SELECT", "Single Select");
        type.setId(TYPE_ID);

        given(optionGroupTypeRepository.findById(TYPE_ID)).willReturn(Optional.of(type));
        given(optionGroupRepository.existsByTypeId(TYPE_ID)).willReturn(true);

        assertThatThrownBy(() -> optionGroupTypeService.deleteType(null, TYPE_ID))
                .isInstanceOf(OptionGroupTypeDeletionBlockedException.class)
                .hasMessage("Option group type cannot be deleted while it is still used by option groups");

        verify(optionGroupTypeRepository, never()).delete(any(OptionGroupType.class));
    }

    private OptionGroupType type(String code, String name) {
        OptionGroupType type = new OptionGroupType();
        type.setCode(code);
        type.setName(name);
        type.setDescription(name + " description");
        return type;
    }
}
