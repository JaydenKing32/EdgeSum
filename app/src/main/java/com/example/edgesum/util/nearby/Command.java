package com.example.edgesum.util.nearby;

public enum Command {
    ERROR, // Error during transfer
    SUMMARISE, // Summarise the transferred file
    SUMMARISE_SEGMENT, // Summarise the transferred file as a video segment
    COMPLETE, // Completed file transfer
    RETURN, // Returning summarised file
    NO_ACTIVITY
}
