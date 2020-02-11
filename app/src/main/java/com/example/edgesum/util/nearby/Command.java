package com.example.edgesum.util.nearby;

public enum Command {
    ERROR, // Error during transfer
    SUMMARISE, // Summarise the transferred file
    COMPLETE, // Completed file transfer
    RETURN, // Returning summarised file
    NO_ACTIVITY
}
