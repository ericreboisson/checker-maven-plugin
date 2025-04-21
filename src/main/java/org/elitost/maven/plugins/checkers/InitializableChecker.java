package org.elitost.maven.plugins.checkers;

import org.apache.maven.plugin.logging.Log;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.elitost.maven.plugins.renderers.ReportRenderer;

import java.util.List;

/**
 * Interface pour les checkers nécessitant Aether et un rendu.
 */
public interface InitializableChecker {
    void init(Log log,
              RepositorySystem repoSystem,
              RepositorySystemSession session,
              List<RemoteRepository> remoteRepositories,
              ReportRenderer renderer);
}