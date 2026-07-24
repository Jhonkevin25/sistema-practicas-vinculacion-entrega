package ec.edu.unibe.sistema_practicas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class SistemaPracticasApplication {

	public static void main(String[] args) {
		SpringApplication.run(SistemaPracticasApplication.class, args);
	}

}
