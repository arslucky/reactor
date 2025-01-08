package org.ars.example.reactor;

import org.ars.example.reactor.entity.Student;
import org.ars.example.reactor.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class StudentControllerTest {

    @Autowired
    private WebTestClient webTestClient;
    @Autowired
    private StudentRepository studentRepository;
    @LocalServerPort
    private int port;

    private Student john, david, simon;
    @BeforeEach
    void init() {
        john = Student.builder().id(1L).name("John").age(23).build();
        david = Student.builder().id(2L).name("David").age(29).build();
        simon = Student.builder().id(3L).name("Simon").age(35).build();
        studentRepository.saveAll(List.of(john, david, simon));
    }
    @Test
    void getStudent() {
        webTestClient
                .get()
                .uri("/students/getStudent/2")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(Student.class).value(student -> assertThat(student).isEqualTo(david));
    }

    @Test
    void getAllStudents() {
        webTestClient
                .get()
                .uri("/students/getAllStudents")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(Student[].class).value(students -> assertThat(students).containsExactlyInAnyOrder(john, david, simon));
    }
}
