#include <stdio.h>
#include <string.h>

#define MAX_CMD 512

int main(int args, char *argv[]) {
    if (args < 2) 
        printf("Usage: my_sudo [cmd] [arg1 arg2 ...]");

    // cmd here is the shell cmd that you want execute in "my_pro"
    // you can check the shell cmd privilege here
    // example:  if (argv[1] != "yum") return; we just allow yum execute here

    char cmd[MAX_CMD];
    strcat(cmd, argv[1]);
    
    int i;
    for (i = 2; i < args; i ++) {
    // concatenate the cmd, example: "yum install xxxxx"
        strcat(cmd, " ");
        strcat(cmd, argv[i]);
    }
    
    //printf("%s", cmd);
    system(cmd);
} 
