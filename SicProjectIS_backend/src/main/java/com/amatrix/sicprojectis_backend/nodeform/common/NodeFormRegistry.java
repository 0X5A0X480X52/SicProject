package com.amatrix.sicprojectis_backend.nodeform.common;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sicprojectis_backend.nodeform.acceptance.AcceptanceNodeFormCatalog;
import com.amatrix.sicprojectis_backend.nodeform.application.ApplicationNodeFormCatalog;
import com.amatrix.sicprojectis_backend.nodeform.contract.ContractNodeFormCatalog;

@Component
public class NodeFormRegistry {
    private final List<NodeFormDefinition> definitions;
    private final Map<String, NodeFormDefinition> byCode;

    public NodeFormRegistry() {
        this.definitions = Stream.of(
                ApplicationNodeFormCatalog.definitions(),
                ContractNodeFormCatalog.definitions(),
                AcceptanceNodeFormCatalog.definitions())
                .flatMap(List::stream)
                .sorted(Comparator.comparing(NodeFormDefinition::moduleType).thenComparing(NodeFormDefinition::formCode))
                .toList();
        this.byCode = definitions.stream().collect(Collectors.toUnmodifiableMap(NodeFormDefinition::formCode, Function.identity()));
    }

    public List<NodeFormDefinition> definitions() {
        return definitions;
    }

    public NodeFormDefinition require(String formCode) {
        NodeFormDefinition definition = byCode.get(formCode);
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Node form not found: " + formCode);
        }
        return definition;
    }
}
