package org.ars.example.reactor.controller;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.ars.example.reactor.entity.Student;
import org.ars.example.reactor.repository.StudentRepository;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Log4j2
@RestController
@RequestMapping("/students")
@AllArgsConstructor
public class StudentController {

    private final StudentRepository studentRepository;
    private Environment env;

    @GetMapping(value = "getStudent/{id}")
    public Mono<ResponseEntity<Student>> getStudent(@PathVariable long id) {
        var student = studentRepository.findById(id);
        return Mono.just(student.map(ResponseEntity::ok).orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND)));
    }

    @GetMapping(value = "getAllStudents")
    public Flux<Student> getAllStudents() {
        log.info("getAllStudents");
        return Flux.fromIterable(studentRepository.findAll());
    }
}
