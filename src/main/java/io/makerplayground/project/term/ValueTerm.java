package io.makerplayground.project.term;

import io.makerplayground.project.ProjectValue;

public class ValueTerm extends Term {

    public ValueTerm(ProjectValue value) {
        super(Type.VALUE, value);
    }

    @Override
    public ProjectValue getValue() { return (ProjectValue) value;
    }

    @Override
    public String toCCode(){
        return  "_" + ((ProjectValue) value).getDevice().getName().replace(" ", "_") + ".get"
                + ((ProjectValue) value).getValue().getName().replace(" ", "_") + "()";
    }
}