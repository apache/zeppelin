grammar Zql;

tokens {
	PIPE  = '|';
	AND   = '&&';
	OR    = '||';
	SEMI  = ';';
}

@lexer::header {
	package org.apache.zeppelin.zql;
}

@parser::header {
	package org.apache.zeppelin.zql;
}


// --- | --- [ && ]
start_fule : zql;

zql returns [List<String> result]
@init {
    result = new ArrayList<String>();
}
    : piped | '[' logical ']';

logical : zql ( (AND | OR) zql)*;
piped : hql (PIPE hql)*;

hql returns [String hql]
    : ~(PIPE|AND|OR|SEMI|'['|']')* 
    /*: STRING {hql = new String($STRING.text);}*/
    ;



/*------------------------------------------------------------------
 * LEXER RULES
 *------------------------------------------------------------------*/

NUMBER  : (DIGIT)+ ;

WHITESPACE : ( '\t' | ' ' | '\r' | '\n'| '\u000C' )+    { $channel = HIDDEN; } ;


fragment DIGIT  : '0'..'9' ;
 
fragment HEX_DIGIT : ('0'..'9'|'a'..'f'|'A'..'F') ;
   
STRING
    :  '"' ( ESC_SEQ | ~('\\'|'"') )* '"'
    ;
    
fragment ESC_SEQ
    :   '\\' ('b'|'t'|'n'|'f'|'r'|'\"'|'\''|'\\')
    |   UNICODE_ESC
    |   OCTAL_ESC
    ;
    
fragment UNICODE_ESC
    :   '\\' 'u' HEX_DIGIT HEX_DIGIT HEX_DIGIT HEX_DIGIT
    ;
    
fragment OCTAL_ESC
    :   '\\' ('0'..'3') ('0'..'7') ('0'..'7')
    |   '\\' ('0'..'7') ('0'..'7')
    |   '\\' ('0'..'7')
    ;

