package net.staro.nomnom.api;

import net.staro.nomnom.Nomnom;

/**
 * Represents a manager responsible for creating instances of a certain aspect of the project.
 */
public interface Manager {
    /**
     * Initializes the manager with the provided Nomnom instance
     *
     * @param nomnom The Nomnom instance providing access to various components and functionalities
     */
    void initialize(Nomnom Nomnom);

}
