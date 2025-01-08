package org.ars.example.reactor.controller;

import lombok.AllArgsConstructor;
import org.ars.example.reactor.entity.Student;
import org.ars.example.reactor.repository.StudentRepository;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
        var students = studentRepository.findAll();
        return Flux.fromIterable(students);
    }
    @GetMapping("callClient")
    public void callClient() {
        var webClient = WebClient.create("http://localhost:" + env.getProperty("server.port"));
        var studentsFlux = webClient
                .get()
                .uri("/students/getAllStudents")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToFlux(Student.class);
        studentsFlux.subscribe(System.out::println);
    }
}
