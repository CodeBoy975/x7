/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.xream.x7.repository.internal;

import io.xream.x7.common.bean.*;
import io.xream.x7.common.bean.condition.InCondition;
import io.xream.x7.common.bean.condition.RefreshCondition;
import io.xream.x7.common.repository.X;
import io.xream.x7.common.util.ExceptionUtil;
import io.xream.x7.common.util.StringUtil;
import io.xream.x7.common.web.Page;
import io.xream.x7.repository.*;
import io.xream.x7.repository.exception.CriteriaSyntaxException;
import io.xream.x7.repository.exception.PersistenceException;
import io.xream.x7.repository.id.IdGeneratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Implement BaseRepository
 *
 * @param <T>
 * @author Sim
 */
public abstract class DefaultRepository<T> implements BaseRepository<T> {

    private final static Logger logger = LoggerFactory.getLogger(BaseRepository.class);

    private Class<T> clz;

    @Override
    public Class<T> getClz() {
        return this.clz;
    }

    public void setClz(Class<T> clz) {
        this.clz = clz;
    }


    private IdGeneratorService idGeneratorService;
    public void setIdGeneratorService(IdGeneratorService  idGeneratorService){
        this.idGeneratorService = idGeneratorService;
    }

    private Repository repository;
    public void setRepository(Repository repository) {
        this.repository =repository;
    }

    private DomainObjectRepositoy domainObjectRepositoy;
    public void setDomainObjectRepositoy(DomainObjectRepositoy domainObjectRepositoy){
        this.domainObjectRepositoy = domainObjectRepositoy;
    }

    public DefaultRepository(){
        parse();
    }

    private void parse(){
        Type genType = getClass().getGenericSuperclass();

        Type[] params = ((ParameterizedType) genType).getActualTypeArguments();

        if (! (params[0] instanceof Class))
            return;
        this.clz = (Class) params[0];

        hook();

    }

    protected void hook() {
        if (!EntityHolder.listAll().contains(this.clz)) {
            EntityHolder.listAll().add(this.clz);
        }
        if (!HealthChecker.getRepositoryList().contains(this)) {
            HealthChecker.getRepositoryList().add(this);
        }
    }


    @Override
    public long createId() {

        final String clzName = this.clz.getName();

        if (this.idGeneratorService.getIdGeneratorPolicy() == null) {
            throw new RuntimeException("No implements of IdGeneratorService, like the project jdbc-template-plus/redis-integration");
        }
        final long id = this.idGeneratorService.createId(clzName);

        if (id == 0)
            throw new PersistenceException("UNEXPECTED EXCEPTION WHILE CREATING ID");

        GlobalIdPersistencePolicy.persist(id, clzName);

        return id;
    }

    @Override
    public boolean createBatch(List<T> objList) {
        return repository.createBatch(objList);
    }

    @Override
    public long create(T obj) {
        /*
         * FIXME
         */
        logger.info("BaesRepository.create: " + obj);

        long id = repository.create(obj);

        return id;

    }


    @Override
    public boolean refresh(RefreshCondition refreshCondition) {

        refreshCondition.setClz(this.clz);
        Parsed parsed = Parser.get(this.clz);
        Field keyField = parsed.getKeyField(X.KEY_ONE);
        if (Objects.isNull(keyField))
            throw new CriteriaSyntaxException("No PrimaryKey, UnSafe Refresh, try to invoke DefaultRepository.refreshUnSafe(RefreshCondition<T> refreshCondition)");

        CriteriaCondition criteriaCondition = refreshCondition.getCondition();

        boolean unSafe = true;//Safe

        if (unSafe) {
            String key = parsed.getKey(X.KEY_ONE);
            for (Criteria.X x : criteriaCondition.getListX()) {
                if (key.equals(x.getKey())) {
                    Object value = x.getValue();
                    if (Objects.nonNull(value) && !value.toString().equals("0")) {
                        unSafe = false;//Safe
                    }
                }
            }
        }

        if (unSafe)
            throw new CriteriaSyntaxException("UnSafe Refresh, try to invoke DefaultRepository.refreshUnSafe(RefreshCondition<T> refreshCondition)");

        return repository.refresh(refreshCondition);
    }

    @Override
    public boolean refreshUnSafe(RefreshCondition<T> refreshCondition) {
        refreshCondition.setClz(this.clz);
        return repository.refresh(refreshCondition);
    }


    @Override
    public boolean remove(String keyOne) {

        if (StringUtil.isNullOrEmpty(keyOne))
            return false;

        return repository.remove(new KeyOne<T>() {

            @Override
            public Object get() {
                return keyOne;
            }

            @Override
            public Class<T> getClzz() {
                return clz;
            }
        });
    }

    @Override
    public boolean remove(long  keyOne) {

        if (keyOne == 0)
            return false;

        return repository.remove(new KeyOne<T>() {
            @Override
            public Object get() {
                return keyOne;
            }

            @Override
            public Class<T> getClzz() {
                return clz;
            }
        });
    }

    @Override
    public T get(long keyOne) {

        if (keyOne == 0)
            return null;

        return repository.get(new KeyOne<T>() {
            @Override
            public Object get() {
                return keyOne;
            }

            @Override
            public Class<T> getClzz() {
                return clz;
            }
        });
    }

    @Override
    public T get(String keyOne) {

        if (StringUtil.isNullOrEmpty(keyOne))
            return null;

        return repository.get(new KeyOne<T>() {
            @Override
            public Object get() {
                return keyOne;
            }

            @Override
            public Class<T> getClzz() {
                return clz;
            }
        });
    }

    @Override
    public List<T> list() {

        T t = null;
        try {
            t = this.clz.newInstance();
        } catch (InstantiationException e) {
            throw new RuntimeException(ExceptionUtil.getMessage(e));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(ExceptionUtil.getMessage(e));
        }
        return repository.list(t);
    }

    @Override
    public List<T> list(T conditionObj) {

        if (conditionObj instanceof Criteria.ResultMappedCriteria) {
            throw new CriteriaSyntaxException(
                    "Exception supported, no page not to invoke repository.list(resultMappedCriteria);");
        }

        return repository.list(conditionObj);
    }

    @Override
    public T getOne(T conditionObj) {
        if (conditionObj == null)
            return null;
        return repository.getOne(conditionObj);
    }

    @Override
    public void refreshCache() {
        repository.refreshCache(this.clz);
    }


    @Override
    public List<T> in(InCondition inCondition) {
        inCondition.setClz(this.clz);
        return repository.in(inCondition);
    }


    @Override
    public Page<T> find(Criteria criteria) {

        if (criteria instanceof Criteria.ResultMappedCriteria)
            throw new CriteriaSyntaxException("Codeing Exception: maybe {Criteria.ResultMappedCriteria criteria = builder.get();} instead of {Criteria criteria = builder.get();}");
        return repository.find(criteria);
    }


    @Override
    public Page<Map<String, Object>> find(Criteria.ResultMappedCriteria criteria) {
        criteria.setClz(this.clz);
        return repository.find(criteria);
    }


    @Override
    public List<Map<String, Object>> list(Criteria.ResultMappedCriteria resultMapped) {
        resultMapped.setClz(this.clz);
        return repository.list(resultMapped);
    }

    @Override
    public List<T> list(Criteria criteria) {

        if (criteria instanceof Criteria.ResultMappedCriteria)
            throw new CriteriaSyntaxException("Codeing Exception: maybe {Criteria.ResultMappedCriteria criteria = builder.get();} instead of {Criteria criteria = builder.get();}");

        return repository.list(criteria);

    }


    @Override
    public <WITH> List<DomainObject<T, WITH>> listDomainObject(Criteria.DomainObjectCriteria domainObjectCriteria) {

        if (StringUtil.isNullOrEmpty(domainObjectCriteria.getMainPropperty()))
            throw new CriteriaSyntaxException("DefaultRepository.listDomainObject(domainObjectCriteria), domainObjectCriteria.getMainPropperty()is null");

        if (domainObjectCriteria.getRelativeClz() == null){

            if (domainObjectCriteria.getKnownMainIdList() == null || domainObjectCriteria.getKnownMainIdList().isEmpty()){
                return domainObjectRepositoy.listDomainObject_NonRelative(domainObjectCriteria);
            }else{
                return domainObjectRepositoy.listDomainObject_Known_NonRelative(domainObjectCriteria);
            }

        }else{
            if (domainObjectCriteria.getKnownMainIdList() == null || domainObjectCriteria.getKnownMainIdList().isEmpty()){
                return domainObjectRepositoy.listDomainObject_HasRelative(domainObjectCriteria);
            }else{
                return domainObjectRepositoy.listDomainObject_Known_HasRelative(domainObjectCriteria);
            }
        }

    }


}
