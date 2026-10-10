#include<bits/stdc++.h>

using namespace std;


string fixString(string exp){

    vector<string> stk;

    for(int i =0;i<exp.size();i++){
        char ch = exp[i];

        if( (ch - 'a' >=0 && ch - 'a' <26) || ch == '+' || ch == '-' || ch =='*' || ch == '/' || ch =='('){
            string str = "";
            str.push_back(ch);
            stk.push_back(str);
        }else{
            // pop from the stk until open bracket
            string str = "";
            int topBracketIfAny = 0;
            while(stk.size()>0 && stk.back() != "("){
                string backStr = stk.back();
                stk.pop_back();
                if(backStr.back() == ')') topBracketIfAny = max(topBracketIfAny, 1);
                if(backStr.back() == '}') topBracketIfAny = max(topBracketIfAny, 2);
                str = str + backStr;
            }
            char endBracket = ')';
            char endopen = '(';
            if(topBracketIfAny == 0) {endBracket = ')'; endopen = '('; }
            if(topBracketIfAny == 1) {endBracket = '}'; endopen = '}'; } 
            if(topBracketIfAny == 2) { endBracket = ']';  endopen = '[';} 
            cout<<" before pushing ot the stk "<<i<<" str "<<str<<" top Bracket"<<topBracketIfAny<<endl;
            str.push_back(endBracket);
            cout<<" before pushing 2 ot the stk "<<i<<" str "<<str<<" top Bracket"<<topBracketIfAny<<endl;
            reverse(str.begin(), str.end());
            cout<<" before pushing 3 ot the stk "<<i<<" str "<<str<<" top Bracket"<<topBracketIfAny<<endl;
            for(int j =0;j<str.size();j++){
                if(str[j] == '(') { str[j] = ')'; continue;}
                if(str[j] == ')') { str[j] = '('; continue;}
                if(str[j] == '{') { str[j] = '}'; continue;}
                if(str[j] == '}') { str[j] = '{'; continue;}
            }
            cout<<" before pushing 4 ot the stk "<<i<<" str "<<str<<" top Bracket"<<topBracketIfAny<<endl;
            str.push_back(endBracket);
            stk.pop_back();
            stk.push_back(str);
            cout<<" after pushing ot the stk "<<i<<" str "<<str<<" top Bracket"<<topBracketIfAny<<endl;
        }
        
    }


    string ans;
    cout<<"printing the vector"<<endl;
    for(int i=0;i<stk.size();i++){
        cout<<"i "<<i<<"     "<<stk[i]<<" ";
        ans  = ans + stk[i];

    }

    cout<<endl;

    return ans;
}

int main(){
    string s= "(((a+b)+c)*d)";

    string ans = fixString(s);
    cout<<"final string is"<<endl;
    cout<<ans<<endl;
}
