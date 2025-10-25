package org.cheetah.sword.service.records;

import com.squareup.javapoet.TypeName;

public  record ScalarFieldInfo(String javaFieldName, TypeName javaType) { }