package org.example.testSelection;

public class testSelectionApplication {
    public static void main(String[] args) throws Exception {

        if (args.length != 3) {
            throw new Exception("参数输错了");
        }else {
            if (args[0].equals("-c")){
                
            }else if (args[0].equals("-m")){

            }
        }
        System.out.print(args[0] + "--" + args[1] + "--" + args[2]);
    }
}
