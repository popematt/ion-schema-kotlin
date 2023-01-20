package com.amazon.ionschema.internal.model.safe2;

import com.amazon.ion.*;
import com.amazon.ionschema.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AndesArnFinder {

    public AndesArnFinder(Type andesArn) {
        this.andesArn = andesArn;
    }

    Type andesArn;

    List<String> findArns(IonValue ion) {
        if (ion.isNullValue()) {
            return Collections.emptyList();
        } else if (ion instanceof IonContainer) {
            List<String> arns = new ArrayList<>();
            for (IonValue child : ((IonContainer) ion)) {
                arns.addAll(findArns(child));
            }
            return arns;
        } else if (ion instanceof IonString && andesArn.isValid(ion)) {
            return Collections.singletonList(((IonString) ion).stringValue());
        }
        return Collections.emptyList();
    }
}
