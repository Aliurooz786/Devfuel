package com.devfuel.weight;

import com.devfuel.log.dto.CreateLogResponse;
import com.devfuel.weight.dto.CreateWeightRequest;
import com.devfuel.weight.dto.WeightHistoryResponse;
import com.devfuel.weight.dto.WeightPointResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weight")
public class WeightController {

    private final WeightService weightService;

    public WeightController(WeightService weightService) {
        this.weightService = weightService;
    }

    @PostMapping
    public ResponseEntity<CreateLogResponse> create(@Valid @RequestBody CreateWeightRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(weightService.create(request));
    }

    @GetMapping("/latest")
    public ResponseEntity<WeightPointResponse> latest() {
        return ResponseEntity.ok(weightService.latest());
    }

    @GetMapping
    public ResponseEntity<WeightHistoryResponse> history(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to
    ) {
        return ResponseEntity.ok(weightService.history(from, to));
    }
}
