package com.example.lifecycle.domain;

import com.example.lifecycle.support.Seq;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** The other half of the A&lt;-&gt;B cycle. See {@link CircularA}. */
@Component
public class CircularB {

    @Autowired
    private CircularA a;

    public CircularB() {
        Seq.step("1'", "circularB", "constructed (second of the A<->B cycle; wired with A's EARLY reference)");
    }

    @PostConstruct
    public void init() {
        Seq.step("7b", "circularB", "@PostConstruct: a is wired? " + (a != null));
    }
}
