package org.ars.example.reactor;

import lombok.extern.log4j.Log4j2;
import org.ars.example.reactor.entity.Student;
import org.ars.example.reactor.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.CoreSubscriber;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Log4j2
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

    @Test
    void getAllStudentsFlux() throws InterruptedException {
        var webClient = WebClient.create("http://localhost:" + port);
        var studentsFlux = webClient
                .get()
                .uri("/students/getAllStudents")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToFlux(Student.class);
        studentsFlux.subscribe(new CoreSubscriber<>() {
            Subscription subscription;
            int count;
            @Override
            public void onSubscribe(Subscription s) {
                log.info("onSubscribe");
                this.subscription = s;
                s.request(1L);
            }
            @Override
            public void onNext(Student student) {
                log.info("onNext");
                log.info(student);
                if (count++ < 1) {
                    try {
                        log.info("sleeping 1 sec");
                        Thread.sleep(1000L);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                this.subscription.request(2L);
            }
            @Override
            public void onError(Throwable throwable) {
                log.error(throwable);
            }
            @Override
            public void onComplete() {
                log.info("onComplete");
            }
        });

        Thread.sleep(2000L);
    }
}
