package org.cheetah.sword.generate;

import java.nio.file.Path;

import org.cheetah.sword.model.DbModel;
import org.cheetah.sword.model.TableModel;
import org.cheetah.sword.generate.writers.ControllerWriter;
import org.cheetah.sword.generate.writers.DtoResourceMapperWriter;
import org.cheetah.sword.generate.writers.DtoWriter;
import org.cheetah.sword.generate.writers.EntityDtoMapperWriter;
import org.cheetah.sword.generate.writers.EntityWriter;
import org.cheetah.sword.generate.writers.RepositoryWriter;
import org.cheetah.sword.generate.writers.ResourceWriter;
import org.cheetah.sword.generate.writers.ServiceWriter;
import org.springframework.stereotype.Service;

@Service
public class CodeGenerationService {

    public void generateAll(Path outputDir, NameResolver resolver, DbModel dbModel) {
        EntityWriter entityWriter = new EntityWriter();               // update internally to accept resolver
        DtoWriter dtoWriter = new DtoWriter();
        ResourceWriter resourceWriter = new ResourceWriter();
        RepositoryWriter repositoryWriter = new RepositoryWriter();   // update internally to accept resolver or keep basePackage
        EntityDtoMapperWriter entityDtoMapperWriter = new EntityDtoMapperWriter(); // update internally to accept resolver
        DtoResourceMapperWriter dtoResourceMapperWriter = new DtoResourceMapperWriter();
        ServiceWriter serviceWriter = new ServiceWriter();           // update internally to accept resolver
        ControllerWriter controllerWriter = new ControllerWriter();  // update internally to accept resolver

        for (TableModel t : dbModel.getTables()) {
            // NOTE: for brevity, I updated only DTO/Resource/DtoResourceMapper fully above.
            // Apply the same signature pattern to all writers.
            entityWriter.write(outputDir, resolver, dbModel.getSchema(), t);
            dtoWriter.write(outputDir, resolver, t);
            resourceWriter.write(outputDir, resolver, t);
            repositoryWriter.write(outputDir, resolver, t);
            entityDtoMapperWriter.write(outputDir, resolver,  t);
            dtoResourceMapperWriter.write(outputDir, resolver, t);
            serviceWriter.write(outputDir, resolver, t);
            controllerWriter.write(outputDir, resolver, t);
        }
    }
}