package com.chequetrack.organization.scope;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.transaction.TransactionManagerCustomizers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** Replaces Spring Boot's default JPA transaction manager with the branch-filtering one. */
@Configuration
public class BranchScopeConfig {

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf,
                                                         ObjectProvider<TransactionManagerCustomizers> customizers) {
        BranchScopeTransactionManager manager = new BranchScopeTransactionManager(emf);
        customizers.ifAvailable(c -> c.customize(manager));
        return manager;
    }
}
