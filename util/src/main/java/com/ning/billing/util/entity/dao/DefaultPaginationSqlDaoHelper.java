/*
 * Copyright 2010-2014 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.util.entity.dao;

import java.util.Iterator;

import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.util.entity.DefaultPagination;
import com.ning.billing.util.entity.Entity;
import com.ning.billing.util.entity.Pagination;

public class DefaultPaginationSqlDaoHelper {

    private final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;

    public DefaultPaginationSqlDaoHelper(final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao) {
        this.transactionalSqlDao = transactionalSqlDao;
    }

    public <E extends Entity, M extends EntityModelDao<E>, S extends EntitySqlDao<M, E>> Pagination<M> getPagination(final Class<? extends EntitySqlDao<M, E>> sqlDaoClazz,
                                                                                                                     final PaginationIteratorBuilder<M, E, S> paginationIteratorBuilder,
                                                                                                                     final Long offset,
                                                                                                                     final Long limit,
                                                                                                                     final InternalTenantContext context) {
        // Note: the connection will be busy as we stream the results out: hence we cannot use
        // SQL_CALC_FOUND_ROWS / FOUND_ROWS on the actual query.
        // We still need to know the actual number of results, mainly for the UI so that it knows if it needs to fetch
        // more pages. To do that, we perform a dummy search query with SQL_CALC_FOUND_ROWS (but limit 1).
        final Long count = transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Long>() {
            @Override
            public Long inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final EntitySqlDao<M, E> sqlDao = entitySqlDaoWrapperFactory.become(sqlDaoClazz);
                return sqlDao.getSearchCount(context);
            }
        });

        // We usually always want to wrap our queries in an EntitySqlDaoTransactionWrapper... except here.
        // Since we want to stream the results out, we don't want to auto-commit when this method returns.
        final EntitySqlDao<M, E> sqlDao = transactionalSqlDao.onDemand(sqlDaoClazz);
        final Long totalCount = sqlDao.getCount(context);
        final Iterator<M> results = paginationIteratorBuilder.build((S) sqlDao, limit);

        return new DefaultPagination<M>(offset, limit, count, totalCount, results);
    }

    public abstract static class PaginationIteratorBuilder<M extends EntityModelDao<E>, E extends Entity, S extends EntitySqlDao<M, E>> {

        public abstract Iterator<M> build(final S sqlDao,
                                          final Long limit);
    }
}
