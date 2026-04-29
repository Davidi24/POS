package pos.pos.menu.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.menu.OptionGroupTypeCodeAlreadyExistsException;
import pos.pos.exception.menu.OptionGroupTypeDeletionBlockedException;
import pos.pos.exception.menu.OptionGroupTypeNameAlreadyExistsException;
import pos.pos.exception.menu.OptionGroupTypeNotFoundException;
import pos.pos.menu.dto.CreateOptionGroupTypeRequest;
import pos.pos.menu.dto.OptionGroupTypeResponse;
import pos.pos.menu.dto.UpdateOptionGroupTypeRequest;
import pos.pos.menu.entity.OptionGroupType;
import pos.pos.menu.mapper.MenuMapper;
import pos.pos.menu.repository.OptionGroupRepository;
import pos.pos.menu.repository.OptionGroupTypeRepository;
import pos.pos.menu.util.MenuCodeNormalizer;
import pos.pos.utils.NormalizationUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OptionGroupTypeService {

    private final OptionGroupTypeRepository optionGroupTypeRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final MenuMapper menuMapper;

    @Transactional(readOnly = true)
    public List<OptionGroupTypeResponse> getTypes(Authentication authentication, String search) {
        String searchLike = NormalizationUtils.normalizeLowerLike(search);
        return (searchLike == null
                ? optionGroupTypeRepository.findAllOrdered()
                : optionGroupTypeRepository.searchByCodeOrName(searchLike))
                .stream()
                .map(menuMapper::toOptionGroupTypeResponse)
                .toList();
    }

    @Transactional
    public OptionGroupTypeResponse createType(Authentication authentication, CreateOptionGroupTypeRequest request) {
        String normalizedName = NormalizationUtils.normalize(request.getName());
        String normalizedCode = resolveCode(request.getCode(), normalizedName, "Name is required");
        assertUniqueCode(normalizedCode, null);
        assertUniqueName(normalizedName, null);

        OptionGroupType type = new OptionGroupType();
        type.setCode(normalizedCode);
        type.setName(normalizedName);
        type.setDescription(NormalizationUtils.normalize(request.getDescription()));

        return menuMapper.toOptionGroupTypeResponse(optionGroupTypeRepository.saveAndFlush(type));
    }

    @Transactional
    public OptionGroupTypeResponse updateType(
            Authentication authentication,
            UUID typeId,
            UpdateOptionGroupTypeRequest request
    ) {
        OptionGroupType type = optionGroupTypeRepository.findById(typeId)
                .orElseThrow(OptionGroupTypeNotFoundException::new);
        String normalizedName = NormalizationUtils.normalize(request.getName());
        String normalizedCode = resolveCode(request.getCode(), normalizedName, "Name is required");
        assertUniqueCode(normalizedCode, typeId);
        assertUniqueName(normalizedName, typeId);

        type.setCode(normalizedCode);
        type.setName(normalizedName);
        type.setDescription(NormalizationUtils.normalize(request.getDescription()));

        return menuMapper.toOptionGroupTypeResponse(optionGroupTypeRepository.saveAndFlush(type));
    }

    @Transactional
    public void deleteType(Authentication authentication, UUID typeId) {
        OptionGroupType type = optionGroupTypeRepository.findById(typeId)
                .orElseThrow(OptionGroupTypeNotFoundException::new);
        if (optionGroupRepository.existsByTypeId(typeId)) {
            throw new OptionGroupTypeDeletionBlockedException();
        }
        optionGroupTypeRepository.delete(type);
    }

    private String resolveCode(String requestedCode, String fallbackName, String emptyMessage) {
        String normalizedCode = MenuCodeNormalizer.normalize(
                NormalizationUtils.normalize(requestedCode) == null ? fallbackName : requestedCode
        );
        if (normalizedCode == null) {
            throw new AuthException(emptyMessage, org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        return normalizedCode;
    }

    private void assertUniqueCode(String code, UUID typeIdToExclude) {
        boolean exists = typeIdToExclude == null
                ? optionGroupTypeRepository.existsByCode(code)
                : optionGroupTypeRepository.existsByCodeAndIdNot(code, typeIdToExclude);
        if (exists) {
            throw new OptionGroupTypeCodeAlreadyExistsException();
        }
    }

    private void assertUniqueName(String name, UUID typeIdToExclude) {
        boolean exists = typeIdToExclude == null
                ? optionGroupTypeRepository.existsByName(name)
                : optionGroupTypeRepository.existsByNameAndIdNot(name, typeIdToExclude);
        if (exists) {
            throw new OptionGroupTypeNameAlreadyExistsException();
        }
    }
}
