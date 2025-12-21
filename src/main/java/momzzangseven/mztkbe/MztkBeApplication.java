package momzzangseven.mztkbe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Application entry point for the MZTK backend service. */
@SpringBootApplication
@EnableJpaAuditing
public class MztkBeApplication {

  public static void main(String[] args) {
    SpringApplication.run(MztkBeApplication.class, args);
  }
}
